package fr.epita.platform.apps.form.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.epita.platform.apps.form.dto.FormDtos.*;
import fr.epita.platform.apps.form.entity.*;
import fr.epita.platform.apps.form.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Structure versionnée : la version publiée est figée ; on édite un brouillon (nouvelle version) puis on le publie.
 * Les réponses restent rattachées à leur version. Le créateur (ou un ADMIN) voit seul les réponses.
 */
@Service
@RequiredArgsConstructor
public class FormService {

    private static final TypeReference<List<FieldDef>> FIELDS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> ANSWERS = new TypeReference<>() {
    };

    private final FormRepository forms;
    private final FormVersionRepository versions;
    private final FormResponseRepository responses;
    private final FormRespondentRepository respondents;
    private final UserLookup userLookup;
    private final ObjectMapper mapper;
    private final java.time.Clock clock;

    // ---------- formulaires ----------

    @Transactional
    public List<FormSummary> list(AuthUser me, Boolean mine) {
        List<Form> list = Boolean.TRUE.equals(mine) ? forms.findByOwnerIdOrderByCreatedAtDesc(me.id()) : forms.findByStatusOrderByCreatedAtDesc(FormStatus.PUBLISHED);
        return list.stream().map(this::refresh).map(f -> new FormSummary(f.getId(), f.getTitle(), f.getStatus(), userLookup.name(f.getOwnerId()),
                f.getOwnerId().equals(me.id()), f.isAnonymous(), f.getPublishedVersion(), f.getDraftVersion(), responses.countByFormId(f.getId()),
                f.getClosesAt(), respondents.existsById(new FormRespondent.Key(f.getId(), me.id())))).toList();
    }

    @Transactional
    public FormDto create(AuthUser me, FormRequest req) {
        Form f = new Form();
        f.setOwnerId(me.id());
        f.setTitle(req.title().trim());
        f.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        f.setAnonymous(req.anonymous());
        f.setOneResponsePerUser(req.oneResponsePerUser());
        f.setClosesAt(req.closesAt());
        f.setStatus(FormStatus.DRAFT);
        f.setDraftVersion(1);
        forms.saveAndFlush(f);
        saveVersion(f.getId(), 1, List.of());
        return toDto(me, f);
    }

    @Transactional
    public FormDto get(AuthUser me, UUID id) {
        Form f = refresh(find(id));
        if (!canManage(me, f) && f.getPublishedVersion() == null) {
            throw ApiException.notFound("Formulaire");
        }
        return toDto(me, f);
    }

    @Transactional
    public FormDto saveStructure(AuthUser me, UUID id, StructureRequest req) {
        Form f = managed(me, id);
        if (f.getDraftVersion() == null) {
            throw ApiException.conflict("FORM_PUBLISHED", "La version publiée n'est plus modifiable : créez une nouvelle version");
        }
        FormRules.validateStructure(req.fields());
        FormVersion v = versions.findByFormIdAndNumber(id, f.getDraftVersion()).orElseThrow();
        v.setFields(write(req.fields()));
        versions.saveAndFlush(v);
        return toDto(me, f);
    }

    @Transactional
    public FormDto publish(AuthUser me, UUID id) {
        Form f = managed(me, id);
        if (f.getStatus() == FormStatus.CLOSED) {
            throw ApiException.conflict("FORM_CLOSED", "Formulaire clos");
        }
        if (f.getDraftVersion() == null) {
            throw ApiException.conflict("FORM_PUBLISHED", "Aucun brouillon à publier");
        }
        List<FieldDef> fields = fieldsOf(id, f.getDraftVersion());
        if (fields.isEmpty()) {
            throw ValidationException.field("fields", "au moins un champ pour publier");
        }
        FormRules.validateStructure(fields);
        f.setPublishedVersion(f.getDraftVersion());
        f.setDraftVersion(null);
        f.setStatus(FormStatus.PUBLISHED);
        return toDto(me, forms.saveAndFlush(f));
    }

    @Transactional
    public FormDto newVersion(AuthUser me, UUID id) {
        Form f = managed(me, id);
        if (f.getPublishedVersion() == null) {
            throw ApiException.conflict("FORM_NOT_PUBLISHED", "Le formulaire n'a pas encore de version publiée");
        }
        if (f.getDraftVersion() != null) {
            throw ApiException.conflict("DRAFT_EXISTS", "Une nouvelle version est déjà en brouillon");
        }
        int next = f.getPublishedVersion() + 1;
        saveVersion(id, next, fieldsOf(id, f.getPublishedVersion()));
        f.setDraftVersion(next);
        return toDto(me, forms.saveAndFlush(f));
    }

    @Transactional
    public FormDto close(AuthUser me, UUID id) {
        Form f = managed(me, id);
        if (f.getStatus() != FormStatus.PUBLISHED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seul un formulaire publié peut être clos");
        }
        f.setStatus(FormStatus.CLOSED);
        return toDto(me, forms.saveAndFlush(f));
    }

    // ---------- réponses ----------

    /** Aperçu : valide selon la version courante (brouillon pour le créateur) sans rien enregistrer. */
    @Transactional(readOnly = true)
    public ValidationResult validate(AuthUser me, UUID id, AnswersRequest req) {
        Form f = find(id);
        int version = canManage(me, f) && f.getDraftVersion() != null ? f.getDraftVersion()
                : Optional.ofNullable(f.getPublishedVersion()).orElseThrow(() -> ApiException.notFound("Formulaire"));
        FormRules.Checked c = FormRules.validateAnswers(fieldsOf(id, version), req.answers());
        return new ValidationResult(c.errors().isEmpty(), c.errors(), c.hidden());
    }

    @Transactional
    public Map<String, Object> respond(AuthUser me, UUID id, AnswersRequest req) {
        Form f = forms.lock(id).orElseThrow(() -> ApiException.notFound("Formulaire"));
        refresh(f);
        if (f.getPublishedVersion() == null) {
            throw ApiException.notFound("Formulaire");
        }
        if (f.getStatus() == FormStatus.CLOSED) {
            throw ApiException.conflict("FORM_CLOSED", "Ce formulaire n'accepte plus de réponse");
        }
        FormRespondent.Key key = new FormRespondent.Key(id, me.id());
        if (f.isOneResponsePerUser() && respondents.existsById(key)) {
            throw ApiException.conflict("ALREADY_RESPONDED", "Vous avez déjà répondu à ce formulaire");
        }
        FormRules.Checked c = FormRules.validateAnswers(fieldsOf(id, f.getPublishedVersion()), req.answers());
        if (!c.errors().isEmpty()) {
            throw new ValidationException(c.errors());
        }
        if (!respondents.existsById(key)) {
            respondents.save(new FormRespondent(key));
        }
        FormResponse r = new FormResponse();
        r.setFormId(id);
        r.setVersion(f.getPublishedVersion());
        r.setUserId(f.isAnonymous() ? null : me.id());
        r.setAnswers(write(c.values()));
        responses.saveAndFlush(r);
        return c.values();
    }

    @Transactional(readOnly = true)
    public PageResponse<ResponseDto> responses(AuthUser me, UUID id, Integer version, int page, int size) {
        Form f = owned(me, id);
        int v = version(f, version);
        return PageResponse.of(responses.findByFormIdAndVersionOrderByCreatedAtDesc(id, v, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))),
                r -> new ResponseDto(r.getId(), r.getVersion(), r.getUserId() == null ? null : userLookup.name(r.getUserId()), r.getCreatedAt(), read(r.getAnswers())));
    }

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me, UUID id, Integer version) {
        Form f = owned(me, id);
        int v = version(f, version);
        List<Map<String, Object>> all = responses.findByFormIdAndVersion(id, v).stream().map(r -> read(r.getAnswers())).toList();
        List<FieldStats> out = new ArrayList<>();
        for (FieldDef field : fieldsOf(id, v)) {
            List<Object> values = all.stream().map(a -> a.get(field.id())).filter(Objects::nonNull).toList();
            Map<String, Long> dist = null;
            Double avg = null;
            Double min = null;
            Double max = null;
            List<String> samples = null;
            switch (field.type()) {
                case SELECT, RADIO, CHECKBOXES -> {
                    dist = new LinkedHashMap<>();
                    for (String o : FormRules.optionValues(field.config() == null ? Map.of() : field.config())) {
                        dist.put(o, 0L);
                    }
                    for (Object val : values) {
                        for (Object one : val instanceof List<?> l ? l : List.of(val)) {
                            dist.merge(String.valueOf(one), 1L, Long::sum);
                        }
                    }
                }
                case NUMBER, RATING -> {
                    DoubleSummaryStatistics s = values.stream().mapToDouble(x -> ((Number) x).doubleValue()).summaryStatistics();
                    if (s.getCount() > 0) {
                        avg = Math.round(s.getAverage() * 100) / 100.0;
                        min = s.getMin();
                        max = s.getMax();
                    }
                    if (field.type() == FieldType.RATING) {
                        dist = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
                        int top = FormRules.num(field.config() == null ? Map.of() : field.config(), "max") == null ? 5
                                : FormRules.num(field.config(), "max").intValue();
                        for (int i = 1; i <= top; i++) {
                            dist.put(String.valueOf(i), 0L);
                        }
                        for (Object val : values) {
                            dist.merge(String.valueOf(((Number) val).intValue()), 1L, Long::sum);
                        }
                    }
                }
                default -> samples = values.stream().map(String::valueOf).limit(5).toList();
            }
            out.add(new FieldStats(field.id(), field.label(), field.type(), values.size(), dist, avg, min, max, samples));
        }
        return new StatsDto(v, all.size(), out);
    }

    public List<FieldTypeDto> fieldTypes() {
        return List.of(
                new FieldTypeDto(FieldType.TEXT, "Texte court", List.of("minLength", "maxLength", "pattern"), "texte"),
                new FieldTypeDto(FieldType.LONG_TEXT, "Texte long", List.of("maxLength"), "texte"),
                new FieldTypeDto(FieldType.NUMBER, "Nombre", List.of("min", "max", "step"), "nombre"),
                new FieldTypeDto(FieldType.EMAIL, "E-mail", List.of(), "texte"),
                new FieldTypeDto(FieldType.DATE, "Date", List.of("min", "max"), "texte AAAA-MM-JJ"),
                new FieldTypeDto(FieldType.SELECT, "Liste déroulante", List.of("options"), "valeur d'une option"),
                new FieldTypeDto(FieldType.RADIO, "Choix unique", List.of("options"), "valeur d'une option"),
                new FieldTypeDto(FieldType.CHECKBOXES, "Cases à cocher", List.of("options", "minSelected", "maxSelected"), "liste de valeurs"),
                new FieldTypeDto(FieldType.RATING, "Note", List.of("max"), "entier de 1 à max"));
    }

    // ---------- outils ----------

    /** Clôture automatique à `closesAt`. */
    private Form refresh(Form f) {
        if (f.getStatus() == FormStatus.PUBLISHED && f.getClosesAt() != null && !clock.instant().isBefore(f.getClosesAt())) {
            f.setStatus(FormStatus.CLOSED);
            forms.save(f);
        }
        return f;
    }

    private int version(Form f, Integer version) {
        int v = version != null ? version : Optional.ofNullable(f.getPublishedVersion()).orElse(1);
        versions.findByFormIdAndNumber(f.getId(), v).orElseThrow(() -> ApiException.notFound("Version"));
        return v;
    }

    private boolean canManage(AuthUser me, Form f) {
        return f.getOwnerId().equals(me.id()) || me.isAdmin();
    }

    private Form find(UUID id) {
        return forms.findById(id).orElseThrow(() -> ApiException.notFound("Formulaire"));
    }

    private Form managed(AuthUser me, UUID id) {
        Form f = forms.lock(id).orElseThrow(() -> ApiException.notFound("Formulaire"));
        if (!canManage(me, f)) {
            throw ApiException.forbidden("Réservé au créateur du formulaire");
        }
        return refresh(f);
    }

    private Form owned(AuthUser me, UUID id) {
        Form f = find(id);
        if (!canManage(me, f)) {
            throw ApiException.forbidden("Seul le créateur voit les réponses");
        }
        return f;
    }

    private void saveVersion(UUID formId, int number, List<FieldDef> fields) {
        FormVersion v = new FormVersion();
        v.setFormId(formId);
        v.setNumber(number);
        v.setFields(write(fields));
        versions.saveAndFlush(v);
    }

    private List<FieldDef> fieldsOf(UUID formId, int version) {
        FormVersion v = versions.findByFormIdAndNumber(formId, version).orElseThrow(() -> ApiException.notFound("Version"));
        try {
            return mapper.readValue(v.getFields(), FIELDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Structure illisible", e);
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json, ANSWERS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private FormDto toDto(AuthUser me, Form f) {
        boolean manage = canManage(me, f);
        int version = manage && f.getDraftVersion() != null ? f.getDraftVersion() : Optional.ofNullable(f.getPublishedVersion()).orElse(1);
        return new FormDto(f.getId(), f.getTitle(), f.getDescription(), f.isAnonymous(), f.isOneResponsePerUser(), f.getClosesAt(), f.getStatus(),
                f.getOwnerId().equals(me.id()), version, manage && f.getDraftVersion() != null && version == f.getDraftVersion(),
                f.getPublishedVersion(), manage ? f.getDraftVersion() : null, fieldsOf(f.getId(), version),
                respondents.existsById(new FormRespondent.Key(f.getId(), me.id())));
    }
}

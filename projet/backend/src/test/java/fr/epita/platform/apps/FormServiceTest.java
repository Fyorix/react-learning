package fr.epita.platform.apps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.form.dto.FormDtos.*;
import fr.epita.platform.apps.form.entity.FieldType;
import fr.epita.platform.apps.form.entity.FormStatus;
import fr.epita.platform.apps.form.service.FormService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormServiceTest extends IntegrationTest {

    @Autowired
    FormService service;
    @Autowired
    ObjectMapper mapper;

    AuthUser owner;
    AuthUser alice;
    AuthUser bob;

    @BeforeEach
    void setUp() {
        owner = auth(user("Olivia Orga"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
    }

    static final List<FieldDef> FIELDS = List.of(
            new FieldDef("vient", FieldType.RADIO, "Venez-vous ?", null, true, Map.of("options", List.of(Map.of("value", "oui", "label", "Oui"), Map.of("value", "non", "label", "Non"))), null),
            new FieldDef("regime", FieldType.SELECT, "Régime", null, true, Map.of("options", List.of(Map.of("value", "standard", "label", "Standard"), Map.of("value", "vegetarien", "label", "Végétarien"))),
                    new Condition("vient", Operator.EQUALS, "oui")),
            new FieldDef("invites", FieldType.NUMBER, "Accompagnants", null, false, Map.of("min", 0, "max", 3, "step", 1), new Condition("vient", Operator.EQUALS, "oui")),
            new FieldDef("note", FieldType.RATING, "Note de l'an dernier", null, false, Map.of("max", 5), null));

    private Map<String, JsonNode> answers(String json) {
        return mapper.convertValue(mapper.valueToTree(mapper.convertValue(read(json), Map.class)), mapper.getTypeFactory().constructMapType(Map.class, String.class, JsonNode.class));
    }

    private Object read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private FormDto published(boolean anonymous) {
        FormDto f = service.create(owner, new FormRequest("Séminaire", null, anonymous, true, null));
        service.saveStructure(owner, f.id(), new StructureRequest(FIELDS));
        return service.publish(owner, f.id());
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void incoherentStructuresAreRejectedWithTheFieldPath() {
        FormDto f = service.create(owner, new FormRequest("Test", null, false, false, null));
        ValidationException e = (ValidationException) org.assertj.core.api.Assertions.catchThrowable(() -> service.saveStructure(owner, f.id(), new StructureRequest(List.of(
                new FieldDef("age", FieldType.NUMBER, "Âge", null, false, Map.of("min", 10, "max", 5), null),
                new FieldDef("age", FieldType.SELECT, "Doublon", null, false, Map.of("options", List.of()), null),
                new FieldDef("x", FieldType.TEXT, "Cond", null, false, Map.of(), new Condition("plusTard", Operator.EQUALS, "a")),
                new FieldDef("plusTard", FieldType.TEXT, "Plus tard", null, false, Map.of(), null)))));
        assertThat(e.getFields()).containsKeys("fields[0].config.max", "fields[1].id", "fields[1].config.options", "fields[2].condition.fieldId");
        assertCode(() -> service.saveStructure(alice, f.id(), new StructureRequest(FIELDS)), "FORBIDDEN");
    }

    @Test
    void answersAreValidatedServerSideAndHiddenFieldsIgnored() {
        FormDto f = published(false);
        ValidationResult missing = service.validate(alice, f.id(), new AnswersRequest(answers("{\"vient\": \"oui\", \"invites\": 5}")));
        assertThat(missing.errors()).containsKeys("answers.regime", "answers.invites");
        ValidationResult hidden = service.validate(alice, f.id(), new AnswersRequest(answers("{\"vient\": \"non\", \"invites\": 9}")));
        assertThat(hidden.valid()).isTrue();
        assertThat(hidden.hiddenFields()).containsExactly("regime", "invites");
        Map<String, Object> saved = service.respond(alice, f.id(), new AnswersRequest(answers("{\"vient\": \"non\", \"regime\": \"vegetarien\", \"note\": 4}")));
        assertThat(saved).containsOnlyKeys("vient", "note");
        assertCode(() -> service.respond(alice, f.id(), new AnswersRequest(answers("{\"vient\": \"non\"}"))), "ALREADY_RESPONDED");
        assertThatThrownBy(() -> service.respond(bob, f.id(), new AnswersRequest(answers("{\"vient\": \"peut-etre\"}")))).isInstanceOf(ValidationException.class);
    }

    @Test
    void publishedStructureIsFrozenAndResponsesStayOnTheirVersion() {
        FormDto f = published(false);
        service.respond(alice, f.id(), new AnswersRequest(answers("{\"vient\": \"oui\", \"regime\": \"standard\", \"invites\": 1}")));
        assertCode(() -> service.saveStructure(owner, f.id(), new StructureRequest(FIELDS)), "FORM_PUBLISHED");
        FormDto v2 = service.newVersion(owner, f.id());
        assertThat(v2.version()).isEqualTo(2);
        assertCode(() -> service.newVersion(owner, f.id()), "DRAFT_EXISTS");
        service.saveStructure(owner, f.id(), new StructureRequest(List.of(FIELDS.get(0))));
        service.publish(owner, f.id());
        service.respond(bob, f.id(), new AnswersRequest(answers("{\"vient\": \"non\"}")));
        assertThat(service.responses(owner, f.id(), 1, 0, 20).totalElements()).isEqualTo(1);
        assertThat(service.responses(owner, f.id(), 2, 0, 20).totalElements()).isEqualTo(1);
        StatsDto stats = service.stats(owner, f.id(), 1);
        assertThat(stats.fields().get(0).distribution()).containsEntry("oui", 1L);
        assertThat(stats.fields().get(2).average()).isEqualTo(1.0);
        assertCode(() -> service.responses(alice, f.id(), 1, 0, 20), "FORBIDDEN");
        service.close(owner, f.id());
        assertThat(service.get(alice, f.id()).status()).isEqualTo(FormStatus.CLOSED);
    }

    @Test
    void anonymousFormsDoNotStoreTheAuthorButStillAllowOneResponse() {
        FormDto f = published(true);
        service.respond(alice, f.id(), new AnswersRequest(answers("{\"vient\": \"non\"}")));
        assertThat(service.responses(owner, f.id(), null, 0, 20).content().get(0).respondentName()).isNull();
        assertCode(() -> service.respond(alice, f.id(), new AnswersRequest(answers("{\"vient\": \"non\"}"))), "ALREADY_RESPONDED");
        service.close(owner, f.id());
        assertCode(() -> service.respond(bob, f.id(), new AnswersRequest(answers("{\"vient\": \"non\"}"))), "FORM_CLOSED");
    }
}

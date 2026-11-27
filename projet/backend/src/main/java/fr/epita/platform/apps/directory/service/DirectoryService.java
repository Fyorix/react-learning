package fr.epita.platform.apps.directory.service;

import fr.epita.platform.apps.directory.dto.DirectoryDtos.AssignmentRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.PersonCardDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.PersonDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.PersonRefDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.ProfileRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.ProfileSkillDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillLevel;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.TeamDto;
import fr.epita.platform.apps.directory.entity.Profile;
import fr.epita.platform.apps.directory.entity.Skill;
import fr.epita.platform.apps.directory.entity.Team;
import fr.epita.platform.apps.directory.repository.ProfileRepository;
import fr.epita.platform.apps.directory.repository.SkillRepository;
import fr.epita.platform.apps.directory.repository.TeamRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.FileStorage;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final ProfileRepository profiles;
    private final TeamRepository teams;
    private final SkillRepository skills;
    private final UserRepository users;
    private final FileStorage storage;

    public record Photo(byte[] content, String contentType) {
    }

    // ---------- recherche ----------

    /** Utilisateurs actifs uniquement ; recherche insensible à la casse et aux accents sur le nom et le poste. */
    @Transactional
    public PageResponse<PersonCardDto> search(String search, UUID teamId, UUID skillId, Integer minLevel, String location,
                                              int page, int size) {
        Map<UUID, Team> teamById = teamMap();
        Map<UUID, Skill> skillById = skillMap();
        String term = normalize(search);
        int level = minLevel == null ? 1 : minLevel;
        List<PersonCardDto> list = users.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .map(u -> Map.entry(u, profileOf(u.getId())))
                .filter(e -> term.isEmpty() || normalize(e.getKey().getDisplayName()).contains(term)
                        || normalize(e.getValue().getJobTitle()).contains(term))
                .filter(e -> teamId == null || teamId.equals(e.getValue().getTeamId()))
                .filter(e -> skillId == null || e.getValue().getSkills().getOrDefault(skillId, 0) >= level)
                .filter(e -> location == null || location.isBlank() || e.getValue().getLocation().equalsIgnoreCase(location.trim()))
                .map(e -> card(e.getKey(), e.getValue(), teamById, skillById)).toList();
        return PageResponse.ofList(list, page, size);
    }

    @Transactional
    public PersonDto get(UUID userId) {
        User u = users.findById(userId).filter(User::isEnabled).orElseThrow(() -> ApiException.notFound("Collaborateur"));
        Profile p = profileOf(userId);
        Map<UUID, Team> teamById = teamMap();
        Map<UUID, Skill> skillById = skillMap();
        PersonRefDto manager = p.getManagerId() == null ? null
                : users.findById(p.getManagerId()).filter(User::isEnabled).map(m -> ref(m, profileOf(m.getId()))).orElse(null);
        List<PersonRefDto> teammates = p.getTeamId() == null ? List.of() : profiles.findByTeamId(p.getTeamId()).stream()
                .filter(x -> !x.getUserId().equals(userId)).map(x -> users.findById(x.getUserId()).filter(User::isEnabled)
                        .map(m -> ref(m, x)).orElse(null)).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(PersonRefDto::displayName)).toList();
        List<PersonRefDto> reports = profiles.findByManagerId(userId).stream().map(x -> users.findById(x.getUserId())
                .filter(User::isEnabled).map(m -> ref(m, x)).orElse(null)).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(PersonRefDto::displayName)).toList();
        Team team = p.getTeamId() == null ? null : teamById.get(p.getTeamId());
        return new PersonDto(u.getId(), u.getDisplayName(), u.getEmail(), p.getJobTitle(), p.getTeamId(),
                team == null ? null : team.getName(), manager, p.getLocation(), p.getPhone(), p.getBio(), p.getPhotoKey() != null,
                skillsOf(p, skillById), teammates, reports);
    }

    // ---------- ma fiche ----------

    @Transactional
    public PersonDto updateMine(AuthUser me, ProfileRequest req) {
        Profile p = profileOf(me.id());
        Set<UUID> seen = new HashSet<>();
        var errors = new ValidationException.Collector();
        for (int i = 0; i < req.skills().size(); i++) {
            SkillLevel s = req.skills().get(i);
            errors.check(seen.add(s.skillId()), "skills[" + i + "].skillId", "compétence déjà déclarée");
            errors.check(skills.existsById(s.skillId()), "skills[" + i + "].skillId", "doit appartenir au référentiel");
        }
        errors.throwIfAny();
        p.setJobTitle(req.jobTitle().trim());
        p.setLocation(req.location().trim());
        p.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        p.setBio(req.bio() == null || req.bio().isBlank() ? null : req.bio().trim());
        Map<UUID, Integer> map = new HashMap<>();
        req.skills().forEach(s -> map.put(s.skillId(), s.level()));
        p.getSkills().clear();
        p.getSkills().putAll(map);
        profiles.saveAndFlush(p);
        return get(me.id());
    }

    @Transactional
    public PersonDto updatePhoto(AuthUser me, MultipartFile file) {
        Profile p = profileOf(me.id());
        FileStorage.Stored stored = storage.store(file, FileStorage.IMAGES);
        if (p.getPhotoKey() != null) {
            storage.delete(p.getPhotoKey());
        }
        p.setPhotoKey(stored.key());
        p.setPhotoType(stored.contentType());
        profiles.saveAndFlush(p);
        return get(me.id());
    }

    @Transactional(readOnly = true)
    public Photo photo(UUID userId) {
        Profile p = profiles.findById(userId).filter(x -> x.getPhotoKey() != null)
                .orElseThrow(() -> ApiException.notFound("Photo"));
        return new Photo(storage.load(p.getPhotoKey()), p.getPhotoType());
    }

    @Transactional
    public PersonDto assign(AuthUser me, UUID userId, AssignmentRequest req) {
        me.requireAdmin();
        users.findById(userId).orElseThrow(() -> ApiException.notFound("Collaborateur"));
        if (req.teamId() != null && !teams.existsById(req.teamId())) {
            throw ApiException.notFound("Équipe");
        }
        if (req.managerId() != null) {
            if (req.managerId().equals(userId)) {
                throw ApiException.conflict("SELF_MANAGER", "Un collaborateur ne peut pas être son propre manager");
            }
            users.findById(req.managerId()).orElseThrow(() -> ApiException.notFound("Manager"));
        }
        Profile p = profileOf(userId);
        p.setTeamId(req.teamId());
        p.setManagerId(req.managerId());
        profiles.saveAndFlush(p);
        return get(userId);
    }

    // ---------- référentiels ----------

    @Transactional(readOnly = true)
    public List<TeamDto> teams() {
        Set<UUID> active = users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(User::getId).collect(Collectors.toSet());
        Map<UUID, Long> counts = profiles.findAll().stream().filter(p -> p.getTeamId() != null && active.contains(p.getUserId()))
                .collect(Collectors.groupingBy(Profile::getTeamId, Collectors.counting()));
        return teams.findAllByOrderByNameAsc().stream().map(t -> new TeamDto(t.getId(), t.getName(), counts.getOrDefault(t.getId(), 0L))).toList();
    }

    @Transactional
    public TeamDto createTeam(AuthUser me, String name) {
        me.requireAdmin();
        if (teams.existsByNameIgnoreCase(name.trim())) {
            throw ApiException.conflict("TEAM_EXISTS", "Cette équipe existe déjà");
        }
        Team t = new Team();
        t.setName(name.trim());
        teams.saveAndFlush(t);
        return new TeamDto(t.getId(), t.getName(), 0);
    }

    @Transactional(readOnly = true)
    public List<SkillDto> skills() {
        return skills.findAllByOrderByNameAsc().stream().map(s -> new SkillDto(s.getId(), s.getName(), s.getCategory())).toList();
    }

    @Transactional
    public SkillDto createSkill(AuthUser me, SkillRequest req) {
        me.requireAdmin();
        if (skills.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("SKILL_EXISTS", "Cette compétence existe déjà");
        }
        Skill s = new Skill();
        s.setName(req.name().trim());
        s.setCategory(req.category() == null || req.category().isBlank() ? null : req.category().trim());
        skills.saveAndFlush(s);
        return new SkillDto(s.getId(), s.getName(), s.getCategory());
    }

    // ---------- outils ----------

    private Profile profileOf(UUID userId) {
        return profiles.findById(userId).orElseGet(() -> profiles.saveAndFlush(new Profile(userId)));
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private Map<UUID, Team> teamMap() {
        return teams.findAll().stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    private Map<UUID, Skill> skillMap() {
        return skills.findAll().stream().collect(Collectors.toMap(Skill::getId, Function.identity()));
    }

    private static List<ProfileSkillDto> skillsOf(Profile p, Map<UUID, Skill> skillById) {
        return p.getSkills().entrySet().stream().filter(e -> skillById.containsKey(e.getKey()))
                .map(e -> {
                    Skill s = skillById.get(e.getKey());
                    return new ProfileSkillDto(s.getId(), s.getName(), s.getCategory(), e.getValue());
                })
                .sorted(Comparator.comparingInt(ProfileSkillDto::level).reversed().thenComparing(ProfileSkillDto::name)).toList();
    }

    private static PersonCardDto card(User u, Profile p, Map<UUID, Team> teamById, Map<UUID, Skill> skillById) {
        Team team = p.getTeamId() == null ? null : teamById.get(p.getTeamId());
        List<ProfileSkillDto> top = skillsOf(p, skillById).stream().limit(3).toList();
        return new PersonCardDto(u.getId(), u.getDisplayName(), p.getJobTitle(), p.getTeamId(), team == null ? null : team.getName(),
                p.getLocation(), p.getPhotoKey() != null, top);
    }

    private static PersonRefDto ref(User u, Profile p) {
        return new PersonRefDto(u.getId(), u.getDisplayName(), p.getJobTitle());
    }
}

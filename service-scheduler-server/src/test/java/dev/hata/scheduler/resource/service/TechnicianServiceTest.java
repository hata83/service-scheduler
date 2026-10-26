package dev.hata.scheduler.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.port.TechnicianProfileProvider;
import dev.hata.scheduler.resource.model.Employee;
import dev.hata.scheduler.resource.model.SkillLevel;
import dev.hata.scheduler.resource.model.Technician;
import dev.hata.scheduler.resource.model.TechnicianSkill;
import dev.hata.scheduler.resource.model.TechnicianSkillCode;
import dev.hata.scheduler.resource.model.TechnicianSkillId;
import dev.hata.scheduler.resource.repository.EmployeeRepository;
import dev.hata.scheduler.resource.repository.TechnicianSkillRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TechnicianServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID TECH_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private EmployeeRepository employees;

    @Mock
    private TechnicianSkillRepository skills;

    private TechnicianService service;

    @BeforeEach
    void setUp() {
        service = new TechnicianService(employees, skills);
    }

    @Test
    void employeeAndTechnicianTaxonomyModelsEnforceScopeAndRanks() {
        assertThat(SkillLevel.SENIOR.getRank()).isEqualTo(4);
        assertThat(SkillLevel.SENIOR.satisfies(SkillLevel.TECHNICIAN)).isTrue();
        assertThat(SkillLevel.JUNIOR.satisfies(SkillLevel.SENIOR)).isFalse();

        Employee admin = new Employee();
        admin.assignEmployee(ADMIN_ID, "ADM-0001", Employee.ROLE_ADMIN, null);
        assertThat(admin.getUserId()).isEqualTo(ADMIN_ID);
        assertThat(admin.getEmployeeId()).isEqualTo("ADM-0001");
        assertThat(admin.getRole()).isEqualTo(Employee.ROLE_ADMIN);
        assertThat(admin.getDealershipId()).isNull();
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(admin.getUpdatedAt()).isNotNull();

        assertThatThrownBy(() -> admin.assignEmployee(ADMIN_ID, "ADM-0001", Employee.ROLE_ADMIN, DEALERSHIP_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("global");

        assertThatThrownBy(() -> admin.assignEmployee(MANAGER_ID, "MGR-0001", Employee.ROLE_MANAGER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local");

        Technician technician = new Technician();
        technician.assignTechnician(TECH_ID, DEALERSHIP_ID, "TEC-0001", SkillLevel.SENIOR);
        assertThat(technician.getUserId()).isEqualTo(TECH_ID);
        assertThat(technician.getDealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(technician.getEmployeeId()).isEqualTo("TEC-0001");
        assertThat(technician.getLevel()).isEqualTo(SkillLevel.SENIOR);
        assertThat(technician.isAvailable()).isTrue();

        TechnicianSkill skill = new TechnicianSkill();
        skill.assign(TECH_ID, TechnicianSkillCode.ENGINE, SkillLevel.SENIOR);
        TechnicianSkillId skillId = new TechnicianSkillId(TECH_ID, TechnicianSkillCode.ENGINE);
        assertThat(skillId.technicianId()).isEqualTo(TECH_ID);
        assertThat(skillId.skill()).isEqualTo(TechnicianSkillCode.ENGINE);
        assertThat(skill.getTechnicianId()).isEqualTo(TECH_ID);
        assertThat(skill.getSkill()).isEqualTo(TechnicianSkillCode.ENGINE);
        assertThat(skill.getLevel()).isEqualTo(SkillLevel.SENIOR);
        assertThat(skill.getCreatedAt()).isNotNull();
        assertThat(skill.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByUserIdMapsManagerAndTechnicianViews() {
        Employee manager = new Employee();
        manager.assignEmployee(MANAGER_ID, "MGR-0001", Employee.ROLE_MANAGER, DEALERSHIP_ID);
        when(employees.findByUserId(MANAGER_ID)).thenReturn(Optional.of(manager));

        Optional<TechnicianProfileProvider.EmployeeView> mgrView = service.findByUserId(MANAGER_ID);
        assertThat(mgrView).isPresent();
        assertThat(mgrView.get().employeeId()).isEqualTo("MGR-0001");
        assertThat(mgrView.get().dealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(mgrView.get().level()).isNull();
        assertThat(mgrView.get().skills()).isEmpty();

        Technician technician = new Technician();
        technician.assignTechnician(TECH_ID, DEALERSHIP_ID, "TEC-0001", SkillLevel.SENIOR);
        TechnicianSkill s1 = new TechnicianSkill();
        s1.assign(TECH_ID, TechnicianSkillCode.ENGINE, SkillLevel.SENIOR);
        TechnicianSkill s2 = new TechnicianSkill();
        s2.assign(TECH_ID, TechnicianSkillCode.BRAKES, SkillLevel.TECHNICIAN);

        when(employees.findByUserId(TECH_ID)).thenReturn(Optional.of(technician));
        when(skills.findByTechnicianId(TECH_ID)).thenReturn(List.of(s1, s2));

        Optional<TechnicianProfileProvider.EmployeeView> techView = service.findByUserId(TECH_ID);
        assertThat(techView).isPresent();
        assertThat(techView.get().dealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(techView.get().level()).isEqualTo("SENIOR");
        assertThat(techView.get().available()).isTrue();
        assertThat(techView.get().skills()).hasSize(2);
    }
}

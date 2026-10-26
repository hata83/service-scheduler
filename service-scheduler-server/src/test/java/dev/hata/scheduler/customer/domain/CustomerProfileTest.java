package dev.hata.scheduler.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CustomerProfileTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void customerProfileCarriesItsColumns() {
        CustomerProfile profile = new CustomerProfile();
        profile.assign(USER_ID, DEALERSHIP_ID, "My", "Nguyen");
        profile.setNames("Tai", "Le");

        assertThat(profile.getUserId()).isEqualTo(USER_ID);
        assertThat(profile.getDealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(profile.getFirstName()).isEqualTo("Tai");
        assertThat(profile.getLastName()).isEqualTo("Le");
        assertThat(profile.getLocale()).isEqualTo("en_US");
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getUpdatedAt()).isNotNull();
    }
}

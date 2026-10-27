package dev.hata.scheduler.customer.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserProfileTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void userProfileCarriesItsColumns() {
        UserProfile profile = new UserProfile();
        profile.assign(USER_ID, "My", "Nguyen");
        profile.setNames("Tai", "Le");

        assertThat(profile.getUserId()).isEqualTo(USER_ID);
        assertThat(profile.getFirstName()).isEqualTo("Tai");
        assertThat(profile.getLastName()).isEqualTo("Le");
        assertThat(profile.getLocale()).isEqualTo("en_US");
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getUpdatedAt()).isNotNull();
    }
}

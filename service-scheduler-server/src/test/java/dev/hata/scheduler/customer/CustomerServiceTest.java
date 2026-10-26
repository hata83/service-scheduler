package dev.hata.scheduler.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.CustomerProfileProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private CustomerProfileRepository repository;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(repository);
    }

    @Test
    void findByUserIdReturnsMappedView() {
        CustomerProfile profile = new CustomerProfile();
        profile.assign(USER_ID, DEALERSHIP_ID, "My", "Nguyen");
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        Optional<CustomerProfileProvider.ProfileView> view = service.findByUserId(USER_ID);

        assertThat(view).isPresent();
        assertThat(view.get().firstName()).isEqualTo("My");
        assertThat(view.get().lastName()).isEqualTo("Nguyen");
    }

    @Test
    void createProfileSavesAndReturnsView() {
        when(repository.save(any(CustomerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerProfileProvider.ProfileView view = service.createProfile(USER_ID, DEALERSHIP_ID, "Tai", "Le");

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.firstName()).isEqualTo("Tai");
        assertThat(view.lastName()).isEqualTo("Le");
        verify(repository).save(any(CustomerProfile.class));
    }

    @Test
    void updateNamesUpdatesExistingProfile() {
        CustomerProfile profile = new CustomerProfile();
        profile.assign(USER_ID, DEALERSHIP_ID, "My", "Nguyen");
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(repository.save(any(CustomerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<CustomerProfileProvider.ProfileView> view = service.updateNames(USER_ID, "Updated", "Name");

        assertThat(view).isPresent();
        assertThat(view.get().firstName()).isEqualTo("Updated");
        assertThat(view.get().lastName()).isEqualTo("Name");
        verify(repository).save(profile);
    }

    @Test
    void updateNamesReturnsEmptyWhenNotFound() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        Optional<CustomerProfileProvider.ProfileView> view = service.updateNames(USER_ID, "Updated", "Name");

        assertThat(view).isEmpty();
    }
}

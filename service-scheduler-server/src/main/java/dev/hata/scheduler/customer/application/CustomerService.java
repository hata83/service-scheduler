package dev.hata.scheduler.customer.application;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.port.CustomerProfileProvider;
import dev.hata.scheduler.customer.domain.CustomerProfile;
import dev.hata.scheduler.customer.domain.CustomerProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer feature domain service (design D4). Implements the shared
 * {@link CustomerProfileProvider} contract so other feature packages can access
 * customer profile data without package coupling.
 */
@Service
public class CustomerService implements CustomerProfileProvider {

    private final CustomerProfileRepository repository;

    public CustomerService(CustomerProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileView> findByUserId(UUID userId) {
        return repository.findByUserId(userId).map(this::toView);
    }

    @Override
    @Transactional
    public ProfileView createProfile(UUID userId, UUID dealershipId, String firstName, String lastName) {
        CustomerProfile profile = new CustomerProfile();
        profile.assign(userId, dealershipId, firstName, lastName);
        CustomerProfile saved = repository.save(profile);
        return toView(saved);
    }

    @Override
    @Transactional
    public Optional<ProfileView> updateNames(UUID userId, String firstName, String lastName) {
        return repository.findByUserId(userId).map(profile -> {
            profile.setNames(firstName, lastName);
            CustomerProfile updated = repository.save(profile);
            return toView(updated);
        });
    }

    private ProfileView toView(CustomerProfile profile) {
        return new ProfileView(
                profile.getUserId(),
                profile.getDealershipId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getLocale());
    }
}

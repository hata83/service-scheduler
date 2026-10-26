package dev.hata.scheduler.customer.service;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.UserProfileProvider;
import dev.hata.scheduler.customer.model.UserProfile;
import dev.hata.scheduler.customer.repository.UserProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User profile domain service (design D4/D13). Implements the shared
 * {@link UserProfileProvider} contract so other feature packages access
 * centralized user profile data without package coupling.
 */
@Service
public class CustomerService implements UserProfileProvider {

    private final UserProfileRepository repository;

    public CustomerService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileView> findByUserId(UUID userId) {
        return repository.findByUserId(userId).map(this::toView);
    }

    @Override
    @Transactional
    public ProfileView createProfile(UUID userId, String firstName, String lastName) {
        UserProfile profile = new UserProfile();
        profile.assign(userId, firstName, lastName);
        UserProfile saved = repository.save(profile);
        return toView(saved);
    }

    @Override
    @Transactional
    public Optional<ProfileView> updateNames(UUID userId, String firstName, String lastName) {
        return repository.findByUserId(userId).map(profile -> {
            profile.setNames(firstName, lastName);
            UserProfile updated = repository.save(profile);
            return toView(updated);
        });
    }

    private ProfileView toView(UserProfile profile) {
        return new ProfileView(profile.getUserId(), profile.getFirstName(), profile.getLastName(), profile.getLocale());
    }
}

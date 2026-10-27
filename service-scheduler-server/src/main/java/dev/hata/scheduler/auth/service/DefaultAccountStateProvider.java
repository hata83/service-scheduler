package dev.hata.scheduler.auth.service;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.auth.model.UserType;
import dev.hata.scheduler.auth.repository.UserRepository;
import dev.hata.scheduler.common.provider.AccountStateProvider;
import dev.hata.scheduler.common.provider.EmployeeProfileProvider;

import org.springframework.stereotype.Service;

/**
 * Implements {@link AccountStateProvider} to query PostgreSQL for the latest user
 * active status, role, and dealership assignment.
 */
@Service
public class DefaultAccountStateProvider implements AccountStateProvider {

    private final UserRepository users;
    private final EmployeeProfileProvider employees;

    public DefaultAccountStateProvider(UserRepository users, EmployeeProfileProvider employees) {
        this.users = users;
        this.employees = employees;
    }

    @Override
    public Optional<AccountState> getAccountState(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return users.findById(userId).map(user -> {
            if (!user.isActive()) {
                return new AccountState(false, null, null);
            }
            if (user.getType() == UserType.EMPLOYEE) {
                return employees
                        .findScopeByUserId(userId)
                        .map(emp -> new AccountState(true, emp.role(), emp.dealershipId()))
                        .orElse(new AccountState(false, null, null));
            }
            return new AccountState(true, UserType.CUSTOMER.name(), null);
        });
    }
}

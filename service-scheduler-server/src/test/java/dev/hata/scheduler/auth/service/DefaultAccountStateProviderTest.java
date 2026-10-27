package dev.hata.scheduler.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.auth.model.User;
import dev.hata.scheduler.auth.model.UserType;
import dev.hata.scheduler.auth.repository.UserRepository;
import dev.hata.scheduler.common.provider.AccountStateProvider;
import dev.hata.scheduler.common.provider.EmployeeProfileProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultAccountStateProviderTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private UserRepository users;

    @Mock
    private EmployeeProfileProvider employees;

    private DefaultAccountStateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultAccountStateProvider(users, employees);
    }

    @Test
    void getAccountStateReturnsEmptyWhenUserIdNull() {
        assertThat(provider.getAccountState(null)).isEmpty();
        verifyNoInteractions(users, employees);
    }

    @Test
    void getAccountStateReturnsInactiveWhenUserDeactivated() {
        User user = new User();
        user.assign(USER_ID, "+84987654321", UserType.EMPLOYEE);
        user.setActive(false);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));

        Optional<AccountStateProvider.AccountState> state = provider.getAccountState(USER_ID);
        assertThat(state).isPresent();
        assertThat(state.get().active()).isFalse();
        assertThat(state.get().role()).isNull();
        assertThat(state.get().dealershipId()).isNull();
        verifyNoInteractions(employees);
    }

    @Test
    void getAccountStateResolvesCustomerWithoutQueryingEmployees() {
        User user = new User();
        user.assign(USER_ID, "+84987654321", UserType.CUSTOMER);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));

        Optional<AccountStateProvider.AccountState> state = provider.getAccountState(USER_ID);
        assertThat(state).isPresent();
        assertThat(state.get().active()).isTrue();
        assertThat(state.get().role()).isEqualTo("CUSTOMER");
        assertThat(state.get().dealershipId()).isNull();
        verifyNoInteractions(employees);
    }

    @Test
    void getAccountStateResolvesEmployeeScopeWithoutLoadingSkills() {
        User user = new User();
        user.assign(USER_ID, "+84987654321", UserType.EMPLOYEE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(employees.findScopeByUserId(USER_ID))
                .thenReturn(Optional.of(new EmployeeProfileProvider.EmployeeScope("TECHNICIAN", DEALERSHIP_ID)));

        Optional<AccountStateProvider.AccountState> state = provider.getAccountState(USER_ID);
        assertThat(state).isPresent();
        assertThat(state.get().active()).isTrue();
        assertThat(state.get().role()).isEqualTo("TECHNICIAN");
        assertThat(state.get().dealershipId()).isEqualTo(DEALERSHIP_ID);
    }

    @Test
    void getAccountStateHandlesMissingEmployeeRecord() {
        User user = new User();
        user.assign(USER_ID, "+84987654321", UserType.EMPLOYEE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(employees.findScopeByUserId(USER_ID)).thenReturn(Optional.empty());

        Optional<AccountStateProvider.AccountState> state = provider.getAccountState(USER_ID);
        assertThat(state).isPresent();
        assertThat(state.get().active()).isFalse();
        assertThat(state.get().role()).isNull();
        assertThat(state.get().dealershipId()).isNull();
    }
}

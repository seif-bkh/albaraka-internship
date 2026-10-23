package com.albaraka.backend.security;

import com.albaraka.backend.model.AdminRole;
import com.albaraka.backend.model.Client;
import com.albaraka.backend.model.User;
import com.albaraka.backend.repository.AdminRoleRepository;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AdminRoleRepository adminRoleRepository;

    public UserDetailsServiceImpl(UserRepository userRepository,
                                  ClientRepository clientRepository,
                                  AdminRoleRepository adminRoleRepository) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.adminRoleRepository = adminRoleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Staff authorities are read from the database every time Spring loads a
        // principal, including each authenticated JWT request and token refresh.
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getRole() == null || user.getRole().isBlank()) {
                throw new UsernameNotFoundException("Staff account has no assigned role");
            }
            AdminRole role = adminRoleRepository.findByCode(user.getRole())
                    .orElseThrow(() -> new UsernameNotFoundException("Assigned staff role no longer exists"));
            return UserDetailsImpl.build(user, role);
        }

        // Customer identities and their enum role remain in the clients table.
        var clientOpt = clientRepository.findByEmail(email);
        if (clientOpt.isPresent()) {
            Client client = clientOpt.get();
            return UserDetailsImpl.build(client);
        }

        throw new UsernameNotFoundException("User Not Found with email: " + email);
    }
}

package com.albaraka.backend.security;

import com.albaraka.backend.model.AdminPermission;
import com.albaraka.backend.model.AdminRole;
import com.albaraka.backend.model.Client;
import com.albaraka.backend.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Auth principal. Staff authorities are re-resolved from the role tables on every JWT request. */
public class UserDetailsImpl implements UserDetails {
    private final Long id;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String email, String password, boolean enabled,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(User user, AdminRole role) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (role.isStaffRole()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
        }
        // Fixed role authorities remain for unrelated legacy admin modules. The
        // scoped Documents/RAGLab endpoints use only PERM_* authorities below.
        if (role.isSystemRole()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
        }
        for (AdminPermission permission : role.getPermissions()) {
            if (AdminPermissionCodes.ALL.contains(permission.getCode())) {
                authorities.add(new SimpleGrantedAuthority(AdminPermissionCodes.authority(permission.getCode())));
            }
        }
        // Manage is a documented permission bundle and always grants View + Run.
        if (authorities.contains(new SimpleGrantedAuthority(
                AdminPermissionCodes.authority(AdminPermissionCodes.RAGLAB_MANAGE)))) {
            authorities.add(new SimpleGrantedAuthority(
                    AdminPermissionCodes.authority(AdminPermissionCodes.RAGLAB_VIEW)));
            authorities.add(new SimpleGrantedAuthority(
                    AdminPermissionCodes.authority(AdminPermissionCodes.RAGLAB_RUN)));
        }
        return new UserDetailsImpl(user.getId(), user.getEmail(), user.getMotDePasse(),
                user.isActif() && role.isActive(), authorities);
    }

    /** Customer portal identities remain separate from staff role storage. */
    public static UserDetailsImpl build(Client client) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (client.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + client.getRole().name()));
        }
        return new UserDetailsImpl(client.getId(), client.getEmail(), client.getMotDePasse(),
                client.isActif(), authorities);
    }

    public Long getId() { return id; }

    /** Keep this feature scoped to database-backed staff; customer authentication stays unchanged. */
    public boolean isStaffPrincipal() {
        return authorities.stream().anyMatch(authority -> "ROLE_STAFF".equals(authority.getAuthority()));
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}

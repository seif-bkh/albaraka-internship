package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/** A fixed, code-supported capability that can be composed into staff roles. */
@Entity
@Table(name = "admin_permissions")
public class AdminPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 32)
    private String area;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 600)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToMany(mappedBy = "permissions")
    private Set<AdminRole> roles = new HashSet<>();

    protected AdminPermission() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getArea() { return area; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
}

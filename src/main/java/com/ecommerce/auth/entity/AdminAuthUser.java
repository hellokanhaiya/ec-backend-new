package com.ecommerce.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_auth_users")
public class AdminAuthUser extends AbstractAuthUser {
}

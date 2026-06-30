package com.ecommerce.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "consumer_auth_sessions")
public class ConsumerAuthSession extends AbstractAuthSession {
}

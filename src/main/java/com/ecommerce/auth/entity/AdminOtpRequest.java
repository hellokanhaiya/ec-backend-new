package com.ecommerce.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_otp_requests")
public class AdminOtpRequest extends AbstractOtpRequest {
}

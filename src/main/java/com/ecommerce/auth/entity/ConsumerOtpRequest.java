package com.ecommerce.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "consumer_otp_requests")
public class ConsumerOtpRequest extends AbstractOtpRequest {
}

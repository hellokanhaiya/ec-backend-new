package com.ecommerce.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One geographic region covered by a {@link ShippingZone}. {@code country} is required;
 * {@code state} and {@code city} narrow it (null = whole country / whole state). A zone
 * covering "all of India" is one region with country=IN, state=null, city=null.
 */
@Getter
@Setter
@Entity
@Table(name = "shipping_zone_regions")
public class ShippingZoneRegion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country", nullable = false, length = 8)
    private String country;

    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "city", length = 128)
    private String city;
}

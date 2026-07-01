package com.ecommerce.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/config")
public class PublicConfigController {

    @GetMapping("/locations")
    public ResponseEntity<Map<String, Object>> getLocationsConfig() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Location configurations loaded",
            "data", Map.of(
                "IN", new LocationConfig(
                    "India", "Pincode", "^[1-9][0-9]{5}$", true, "State",
                    List.of(
                        new StateConfig("MH", "Maharashtra"),
                        new StateConfig("KA", "Karnataka"),
                        new StateConfig("DL", "Delhi"),
                        new StateConfig("UP", "Uttar Pradesh"),
                        new StateConfig("TN", "Tamil Nadu")
                    )
                ),
                "US", new LocationConfig(
                    "United States", "ZIP Code", "^\\d{5}(-\\d{4})?$", true, "State",
                    List.of(
                        new StateConfig("CA", "California"),
                        new StateConfig("NY", "New York"),
                        new StateConfig("TX", "Texas"),
                        new StateConfig("FL", "Florida")
                    )
                ),
                "GB", new LocationConfig(
                    "United Kingdom", "Postcode", "^[A-Z]{1,2}\\d[A-Z\\d]? ?\\d[A-Z]{2}$", true, "County",
                    List.of(
                        new StateConfig("ENG", "England"),
                        new StateConfig("SCT", "Scotland"),
                        new StateConfig("WLS", "Wales"),
                        new StateConfig("NIR", "Northern Ireland")
                    )
                ),
                "AU", new LocationConfig(
                    "Australia", "Postcode", "^\\d{4}$", true, "State/Territory",
                    List.of(
                        new StateConfig("NSW", "New South Wales"),
                        new StateConfig("VIC", "Victoria"),
                        new StateConfig("QLD", "Queensland"),
                        new StateConfig("WA", "Western Australia")
                    )
                ),
                "NP", new LocationConfig(
                    "Nepal", "Postal Code", "^\\d{5}$", false, "Province",
                    List.of(
                        new StateConfig("P1", "Province No. 1"),
                        new StateConfig("P2", "Madhesh Province"),
                        new StateConfig("P3", "Bagmati Province"),
                        new StateConfig("P4", "Gandaki Province")
                    )
                )
            )
        ));
    }

    public record LocationConfig(
        String name,
        String postalCodeLabel,
        String postalCodeRegex,
        boolean postalCodeRequired,
        String stateLabel,
        List<StateConfig> states
    ) {}

    public record StateConfig(
        String code,
        String name
    ) {}
}

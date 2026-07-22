package com.invsys.integration.easypost;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live EasyPost ship-from defaults (warehouse origin). Required for prod plug-and-play
 * when order/customer address maps are incomplete.
 */
@ConfigurationProperties(prefix = "invsys.easypost")
public class EasyPostProperties {

    private String apiKey = "";
    private FromAddress defaultFrom = new FromAddress();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public FromAddress getDefaultFrom() {
        return defaultFrom;
    }

    public void setDefaultFrom(FromAddress defaultFrom) {
        this.defaultFrom = defaultFrom != null ? defaultFrom : new FromAddress();
    }

    public EasyPostGateway.AddressSpec defaultFromAddress() {
        FromAddress f = defaultFrom;
        if (f.getStreet1() == null || f.getStreet1().isBlank()
                || f.getCity() == null || f.getCity().isBlank()
                || f.getZip() == null || f.getZip().isBlank()) {
            return null;
        }
        return new EasyPostGateway.AddressSpec(
                blankTo(f.getName(), "Warehouse"),
                f.getCompany(),
                f.getStreet1(),
                f.getStreet2(),
                f.getCity(),
                f.getState(),
                f.getZip(),
                blankTo(f.getCountry(), "US"),
                f.getPhone(),
                f.getEmail());
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static class FromAddress {
        private String name = "Warehouse";
        private String company;
        private String street1;
        private String street2;
        private String city;
        private String state;
        private String zip;
        private String country = "US";
        private String phone;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCompany() {
            return company;
        }

        public void setCompany(String company) {
            this.company = company;
        }

        public String getStreet1() {
            return street1;
        }

        public void setStreet1(String street1) {
            this.street1 = street1;
        }

        public String getStreet2() {
            return street2;
        }

        public void setStreet2(String street2) {
            this.street2 = street2;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getZip() {
            return zip;
        }

        public void setZip(String zip) {
            this.zip = zip;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}

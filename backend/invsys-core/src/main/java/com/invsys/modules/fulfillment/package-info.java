@org.springframework.modulith.ApplicationModule(
        displayName = "fulfillment",
        allowedDependencies = { "catalog", "inventory :: api", "inventory :: domain", "sales :: api", "sales :: domain" }
)
package com.invsys.modules.fulfillment;

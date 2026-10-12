@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "events",
            "competition",
            "registrations",
            "results",
            "organizations",
            "identity",
            "common::exception"
        })
package com.acme.sportplatform.scheduling;

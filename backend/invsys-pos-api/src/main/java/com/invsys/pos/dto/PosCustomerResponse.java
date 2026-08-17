package com.invsys.pos.dto;

import java.util.UUID;

public record PosCustomerResponse(UUID id, String name, String email) {
}

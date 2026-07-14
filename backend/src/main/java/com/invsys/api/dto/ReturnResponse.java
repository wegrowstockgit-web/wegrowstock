package com.invsys.api.dto;



import java.math.BigDecimal;

import java.time.Instant;

import java.util.List;

import java.util.UUID;



public record ReturnResponse(

        UUID id,

        UUID salesOrderId,

        String salesOrderNumber,

        String customerName,

        String number,

        String status,

        String returnLabelUrl,

        List<ReturnLineResponse> lines,

        Instant createdAt

) {

}



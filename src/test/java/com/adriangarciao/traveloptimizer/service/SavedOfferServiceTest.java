package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.SavedOfferDTO;
import com.adriangarciao.traveloptimizer.service.SavedOfferService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
public class SavedOfferServiceTest {

    @Autowired
    private SavedOfferService service;

    @Test
    void saveListDeleteLifecycle() {
        String cid = "test-client";
        SavedOfferDTO dto = SavedOfferDTO.builder()
                .tripOptionId(null)
                .origin("ORD")
                .destination("LAX")
                .totalPrice(BigDecimal.valueOf(123.45))
                .currency("USD")
                .airlineCode("NK")
                .airlineName("Spirit")
                .flightNumber("NK 1001")
                .segments("[\"ORD→LAS\",\"LAS→LAX\"]")
                .build();

        SavedOfferDTO saved = service.save(cid, dto);
        Assertions.assertNotNull(saved.getId());

        List<SavedOfferDTO> list = service.list(cid);
        Assertions.assertTrue(list.size() >= 1);

        service.delete(cid, saved.getId());

        List<SavedOfferDTO> after = service.list(cid);
        // deleted item should no longer be present by id
        Assertions.assertTrue(after.stream().noneMatch(s -> s.getId().equals(saved.getId())));
    }
}

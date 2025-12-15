package com.adriangarciao.traveloptimizer.mapper;

import com.adriangarciao.traveloptimizer.dto.FlightSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.LodgingSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.LodgingOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper for converting trip option entities to DTOs and back.
 */
@Mapper(componentModel = "spring")
public interface TripOptionMapper {
    TripOptionMapper INSTANCE = Mappers.getMapper(TripOptionMapper.class);

    @Mapping(target = "tripOptionId", source = "id")
    TripOptionSummaryDTO toDto(TripOption entity);

    @Mapping(target = "id", source = "tripOptionId")
    TripOption toEntity(TripOptionSummaryDTO dto);

    FlightSummaryDTO flightToDto(FlightOption flight);
    FlightOption flightToEntity(FlightSummaryDTO dto);

    LodgingSummaryDTO lodgingToDto(LodgingOption lodging);
    LodgingOption lodgingToEntity(LodgingSummaryDTO dto);
}

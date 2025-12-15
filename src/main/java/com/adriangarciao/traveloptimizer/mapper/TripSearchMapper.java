package com.adriangarciao.traveloptimizer.mapper;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper to convert between TripSearchRequestDTO and TripSearch entity.
 */
@Mapper(componentModel = "spring")
public interface TripSearchMapper {
    TripSearchMapper INSTANCE = Mappers.getMapper(TripSearchMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    TripSearch toEntity(TripSearchRequestDTO dto);
}

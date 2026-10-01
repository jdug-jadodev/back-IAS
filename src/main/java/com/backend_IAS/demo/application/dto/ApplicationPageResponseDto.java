package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationPageResponseDto {
    @JsonProperty("content")
    @Schema(description = "Solicitudes de la página, ordenadas de la más reciente a la más antigua")
    private final List<ApplicationResponseDto> content;
    @JsonProperty("page")
    @Schema(description = "Índice de página, comienza en cero", example = "0", minimum = "0")
    private final int page;
    @JsonProperty("size")
    @Schema(description = "Máximo de solicitudes por página", example = "20", minimum = "1", maximum = "100")
    private final int size;
    @JsonProperty("totalElements")
    @Schema(description = "Total de solicitudes aprobadas y rechazadas", example = "45")
    private final long totalElements;
    @JsonProperty("totalPages")
    @Schema(description = "Total de páginas; cero cuando no hay solicitudes", example = "3")
    private final long totalPages;
    @JsonProperty("first")
    @Schema(description = "La página solicitada es cero", example = "true")
    private final boolean first;
    @JsonProperty("last")
    @Schema(description = "No hay una página posterior con resultados", example = "false")
    private final boolean last;
}

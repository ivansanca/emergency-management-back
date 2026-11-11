package com.emergencias.emergency_management.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Zone")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Zone {

    @Id
    private String ineCode;

    private String name;
    private Double latitude;
    private Double longitude;
}

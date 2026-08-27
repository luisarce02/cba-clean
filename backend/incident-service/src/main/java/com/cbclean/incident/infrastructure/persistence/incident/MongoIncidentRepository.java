package com.cbclean.incident.infrastructure.persistence.incident;

import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentId;
import com.cbclean.incident.domain.repository.IncidentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MongoDB adapter for the domain {@link IncidentRepository} port.
 * Translates the domain aggregate into the Mongo persistence model.
 */
@Repository
public class MongoIncidentRepository implements IncidentRepository {

    private final IncidentMongoRepository mongo;

    public MongoIncidentRepository(IncidentMongoRepository mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(Incident incident) {
        mongo.save(IncidentPersistenceMapper.toDocument(incident));
    }

    @Override
    public Optional<Incident> findById(IncidentId id) {
        return mongo.findById(id.value().toString()).map(IncidentPersistenceMapper::toDomain);
    }

    @Override
    public List<Incident> findAll() {
        return mongo.findAll().stream().map(IncidentPersistenceMapper::toDomain).collect(Collectors.toList());
    }
}

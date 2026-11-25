package cn.innersight.health.service;

import cn.innersight.health.repository.HealthRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final HealthRepository repository;
    public HealthService(HealthRepository repository) { this.repository = repository; }
    public boolean ready() {
        try { repository.ping(); return true; }
        catch (DataAccessException failure) { return false; }
    }
}

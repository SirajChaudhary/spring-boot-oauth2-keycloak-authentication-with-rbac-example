package com.example.controller;

import com.example.dto.Employee;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/*
 *
 * Example users in Keycloak:
 *   - prasad/admin123 → ADMIN
 *   - siraj/user123  → USER
 */
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final Map<Long, Employee> employeeMap = new HashMap<>();
    private long counter = 1;

    // Accessible to USER and ADMIN
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Collection<Employee> getAllEmployees() {
        return employeeMap.values();
    }

    // Accessible to USER and ADMIN
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Employee getEmployee(@PathVariable Long id) {
        return employeeMap.getOrDefault(id, null);
    }

    // Only ADMIN (prasad) can create
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Employee createEmployee(@RequestBody Employee employee) {
        long id = counter++;
        employee.setId(id);
        employeeMap.put(id, employee);
        return employee;
    }

    // Only ADMIN (prasad) can update
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Employee updateEmployee(@PathVariable Long id, @RequestBody Employee employee) {
        if (!employeeMap.containsKey(id)) return null;
        employee.setId(id);
        employeeMap.put(id, employee);
        return employee;
    }

    // Only ADMIN (prasad) can delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> deleteEmployee(@PathVariable Long id) {
        if (employeeMap.remove(id) != null) {
            return Map.of("message", "Deleted employee " + id);
        } else {
            return Map.of("error", "Employee not found");
        }
    }
}

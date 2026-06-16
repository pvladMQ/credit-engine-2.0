package com.tanzu.creditengine.controller;

import com.tanzu.creditengine.service.DataBrowseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read-only API backing the admin portal's database browser.
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataBrowseService dataBrowseService;

    public DataController(DataBrowseService dataBrowseService) {
        this.dataBrowseService = dataBrowseService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> summary() {
        return ResponseEntity.ok(dataBrowseService.counts());
    }

    @GetMapping("/customers")
    public ResponseEntity<List<DataBrowseService.CustomerView>> customers(
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(dataBrowseService.recentCustomers(limit));
    }
}

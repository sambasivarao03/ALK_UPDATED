package com.aadhaar.linkage.service;

import com.aadhaar.linkage.dto.LinkageRequest;
import com.aadhaar.linkage.dto.LinkageResponse;
import com.aadhaar.linkage.model.PersonIdentity;
import com.aadhaar.linkage.repository.LinkageRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LinkageService {

    private final LinkageRepository repo;

    public LinkageService(LinkageRepository repo) {
        this.repo = repo;
    }

    // ---------------- MAIN DISPATCHER ----------------
    public LinkageResponse processRequest(LinkageRequest req) {
        String action = req.getAction().toUpperCase(Locale.ROOT);
        switch (action) {
            case "INSERT":
                return insertRecord(req);
            case "UPDATE":
                return updateRecord(req);
            case "DELETE":
                return deleteRecord(req);
            case "SEARCH":
                return searchRecord(req);
            default:
                return LinkageResponse.error("Invalid action: " + req.getAction());
        }
    }

    // ---------------- INSERT ----------------
    private LinkageResponse insertRecord(LinkageRequest req) {
        Map<String, String> data = req.getData();
        if (data == null || data.isEmpty())
            return LinkageResponse.error("Data is required for INSERT");

        PersonIdentity person = new PersonIdentity();
        person.setAadhaarLinkageKey(UUID.randomUUID().toString());

        // Hash only to simulate privacy
        person.setHashedForename(hash(data.get("forename")));
        person.setHashedDob(hash(data.get("dob")));
        person.setHashedAddress(hash(data.get("address")));
        person.setGender(data.get("gender"));

        // initialize all counters based on availability
        person.setAadhaarCounter(data.containsKey("aadhaar_number") ? 1 : 0);
        person.setPanCounter(data.containsKey("pan_number") ? 1 : 0);
        person.setVoterIdCounter(data.containsKey("voter_id") ? 1 : 0);
        person.setDlCounter(data.containsKey("dl_number") ? 1 : 0);

        repo.save(person);
        return LinkageResponse.success("Record inserted successfully", summary(person));
    }

    // ---------------- UPDATE ----------------
    private LinkageResponse updateRecord(LinkageRequest req) {
        String key = req.getOldAadhaarLinkageKey();
        if (key == null || key.isBlank())
            return LinkageResponse.error("AadhaarLinkageKey required for UPDATE");

        Optional<PersonIdentity> opt = repo.findById(key);
        if (opt.isEmpty())
            return LinkageResponse.error("Record not found for key: " + key);

        PersonIdentity p = opt.get();

        String source = req.getSource().toUpperCase(Locale.ROOT);
        switch (source) {
            case "AADHAAR":
                p.setAadhaarCounter(p.getAadhaarCounter() + 1);
                break;
            case "PAN":
                p.setPanCounter(p.getPanCounter() + 1);
                break;
            case "VOTER":
                p.setVoterIdCounter(p.getVoterIdCounter() + 1);
                break;
            case "DRIVING":
                p.setDlCounter(p.getDlCounter() + 1);
                break;
            default:
                return LinkageResponse.error("Invalid source for update: " + source);
        }

        repo.save(p);
        return LinkageResponse.success("Record updated successfully", summary(p));
    }

    // ---------------- DELETE ----------------
    private LinkageResponse deleteRecord(LinkageRequest req) {
        String key = req.getOldAadhaarLinkageKey();
        if (key == null || key.isBlank())
            return LinkageResponse.error("AadhaarLinkageKey required for DELETE");

        Optional<PersonIdentity> opt = repo.findById(key);
        if (opt.isEmpty())
            return LinkageResponse.error("Record not found for key: " + key);

        PersonIdentity p = opt.get();

        String source = req.getSource().toUpperCase(Locale.ROOT);
        System.out.println("source "+source);
        switch (source) {
            case "AADHAAR":
                p.setAadhaarCounter(Math.max(0, p.getAadhaarCounter() - 1));
                break;
            case "PAN":
                p.setPanCounter(Math.max(0, p.getPanCounter() - 1));
                break;
            case "VOTER":
                p.setVoterIdCounter(Math.max(0, p.getVoterIdCounter() - 1));
                break;
            case "DRIVING":
                p.setDlCounter(Math.max(0, p.getDlCounter() - 1));
                break;
            default:
                return LinkageResponse.error("Invalid source for delete: " + source);
        }

        repo.save(p);
        return LinkageResponse.success("Record deleted successfully", summary(p));
    }

    // ---------------- SEARCH ----------------
    private LinkageResponse searchRecord(LinkageRequest req) {
        String key = req.getOldAadhaarLinkageKey();
        if (key == null || key.isBlank())
            return LinkageResponse.error("AadhaarLinkageKey required for SEARCH");

        Optional<PersonIdentity> opt = repo.findById(key);
        if (opt.isEmpty())
            return LinkageResponse.error("Record not found for key: " + key);

        return LinkageResponse.success("Record found", summary(opt.get()));
    }


    // ---------------- HELPERS ----------------
    private Map<String, Object> summary(PersonIdentity p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("aadhaarLinkageKey", p.getAadhaarLinkageKey());
        map.put("aadhaarCounter", counterLabel(p.getAadhaarCounter()));
        map.put("panCounter", counterLabel(p.getPanCounter()));
        map.put("voterIdCounter", counterLabel(p.getVoterIdCounter()));
        map.put("dlCounter", counterLabel(p.getDlCounter()));
        return map;
    }

    private String counterLabel(int count) {
        return (count <= 0) ? "Data source not available" : String.valueOf(count);
    }

    private String hash(String value) {
        if (value == null) return null;
        return Integer.toHexString(value.hashCode());
    }
}

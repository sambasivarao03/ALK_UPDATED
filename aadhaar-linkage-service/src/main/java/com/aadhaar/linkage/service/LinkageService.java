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
        String action = req.getAction().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "INSERT": return insertRecord(req);
            case "UPDATE": return updateRecord(req);
            case "DELETE": return deleteRecord(req);
            case "SEARCH": return searchRecord(req);
            default: return LinkageResponse.error("Invalid action: " + req.getAction());
        }
    }

    // ---------------- INSERT ----------------
    private LinkageResponse insertRecord(LinkageRequest req) {
        Map<String, String> data = req.getData();
        if (data == null || data.isEmpty())
            return LinkageResponse.error("Data is required for INSERT");

        String forename = normalize(data.get("forename"));
        String dob = normalize(data.get("dob"));
        if (forename == null || dob == null)
            return LinkageResponse.error("Forename and DOB required to identify person");

        String hForename = hash(forename);
        String hDob = hash(dob);

        // Check if person already exists
        Optional<PersonIdentity> existingOpt = repo.findByHashedForenameAndHashedDob(hForename, hDob);
        PersonIdentity person;
        boolean createdNew = false;

        if (existingOpt.isPresent()) {
            person = existingOpt.get();
        } else {
            person = new PersonIdentity();
            person.setAadhaarLinkageKey(UUID.randomUUID().toString());
            person.setHashedForename(hForename);
            person.setHashedSecondname(hash(data.get("secondname")));
            person.setHashedLastname(hash(data.get("lastname")));
            person.setHashedDob(hDob);
            person.setHashedAadhaarNumber(hash(data.get("aadhaar_number")));
            person.setHashedPanNumber(hash(data.get("pan_number")));
            person.setHashedDlNumber(hash(data.get("dl_number")));
            person.setHashedVoterId(hash(data.get("voter_id")));
            person.setHashedAddress(hash(data.get("address")));
            person.setGender(data.get("gender"));
            initCounters(person);
            createdNew = true;
        }

        // Based on source, set counter = 1 if inserting for first time
        String source = req.getSource().toUpperCase(Locale.ROOT);
        switch (source) {
            case "AADHAAR":
                if (createdNew || person.getAadhaarCounter() == 0) {
                    person.setAadhaarCounter(1);
                    person.setHashedAadhaarNumber(hash(data.get("aadhaar_number")));
                }
                break;
            case "PAN":
                if (createdNew || person.getPanCounter() == 0) {
                    person.setPanCounter(1);
                    person.setHashedPanNumber(hash(data.get("pan_number")));
                }
                break;
            case "VOTER":
                if (createdNew || person.getVoterIdCounter() == 0) {
                    person.setVoterIdCounter(1);
                    person.setHashedVoterId(hash(data.get("voter_id")));
                }
                break;
            case "DRIVING":
                if (createdNew || person.getDlCounter() == 0) {
                    person.setDlCounter(1);
                    person.setHashedDlNumber(hash(data.get("dl_number")));
                }
                break;
            default:
                return LinkageResponse.error("Invalid source: " + source);
        }

        repo.save(person);
        return LinkageResponse.success(
                existingOpt.isPresent() ? "Record updated with new source" : "Record inserted successfully",
                summary(person)
        );
    }

    // ---------------- UPDATE ----------------
    
    private LinkageResponse updateRecord(LinkageRequest req) {

        String oldKey = req.getOldAadhaarLinkageKey();
        if (oldKey == null || oldKey.isBlank())
            return LinkageResponse.error("Old AadhaarLinkageKey is required for UPDATE");

        Optional<PersonIdentity> opt = repo.findById(oldKey);
        if (opt.isEmpty())
            return LinkageResponse.error("No record found for key: " + oldKey);

        PersonIdentity oldRecord = opt.get();
        String source = req.getSource().toUpperCase(Locale.ROOT);
        Map<String, String> newData = req.getData();

        // ---------------------------
        // STEP 1: CREATE NEW RECORD
        // ---------------------------
        PersonIdentity newRecord = new PersonIdentity();

        // Copy all old values
        newRecord.setHashedAadhaarNumber(oldRecord.getHashedAadhaarNumber());
        System.out.println("aadhaar number: "+ newRecord.getHashedAadhaarNumber());
        newRecord.setHashedPanNumber(oldRecord.getHashedPanNumber());
        System.out.println("pan number: "+ newRecord.getHashedPanNumber());
        newRecord.setHashedDlNumber(oldRecord.getHashedDlNumber());
        System.out.println("Dl number: "+newRecord.getHashedDlNumber());
        newRecord.setHashedVoterId(oldRecord.getHashedVoterId());

        // Copy counters
        newRecord.setAadhaarCounter(oldRecord.getAadhaarCounter());
        newRecord.setPanCounter(oldRecord.getPanCounter());
        newRecord.setVoterIdCounter(oldRecord.getVoterIdCounter());
        newRecord.setDlCounter(oldRecord.getDlCounter());

        // NEW UNIQUE KEY
        newRecord.setAadhaarLinkageKey(UUID.randomUUID().toString());

        // Replace PII with NEW hashed values
        newRecord.setHashedAddress(hash(newData.get("address")));
        newRecord.setHashedForename(hash(newData.get("forename")));
        newRecord.setHashedSecondname(hash(newData.get("secondname")));
        newRecord.setHashedLastname(hash(newData.get("lastname")));
        newRecord.setGender(newData.get("gender"));
        newRecord.setHashedDob(hash(newData.get("dob")));

        // Reset only the target source counter to 1
        switch (source) {
            case "AADHAAR": 
            	newRecord.setAadhaarCounter(oldRecord.getAadhaarCounter()+1);
            	newRecord.setHashedAadhaarNumber(hash(newData.get("aadhaar_number")));
            	oldRecord.setAadhaarCounter(oldRecord.getAadhaarCounter() - 1);
            	break;
            case "PAN":     
            	newRecord.setPanCounter(oldRecord.getPanCounter()+1); 
            	newRecord.setHashedPanNumber(hash(newData.get("pan_number")));
            	oldRecord.setPanCounter(oldRecord.getPanCounter() - 1);
            	break;
            case "VOTER":   
            	newRecord.setVoterIdCounter(oldRecord.getVoterIdCounter()+1); 
            	newRecord.setHashedVoterId(hash(newData.get("voter_id")));
            	oldRecord.setVoterIdCounter(oldRecord.getVoterIdCounter() - 1);
            	break;
            case "DRIVING": 
            	newRecord.setDlCounter(oldRecord.getDlCounter()+1); 
            	newRecord.setHashedDlNumber(hash(newData.get("dl_number")));
            	oldRecord.setDlCounter(oldRecord.getDlCounter()-1);
            	break;
            default:
                return LinkageResponse.error("Invalid source: " + source);
        }

        repo.save(newRecord);

        // If all counters become zero, delete old
        boolean allZero =
                oldRecord.getAadhaarCounter() == 0 &&
                oldRecord.getPanCounter() == 0 &&
                oldRecord.getVoterIdCounter() == 0 &&
                oldRecord.getDlCounter() == 0;

        if (allZero) {
            repo.delete(oldRecord);
            return LinkageResponse.success(
                    "Update complete. Old record deleted because all counters reached zero.",
                    Map.of("newAadhaarLinkageKey", newRecord.getAadhaarLinkageKey())
            );
        }

        repo.save(oldRecord);

        return LinkageResponse.success(
                "Update complete. Old record updated.",
                Map.of("newAadhaarLinkageKey", newRecord.getAadhaarLinkageKey())
        );
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

        switch (source) {
            case "AADHAAR":
                p.setAadhaarCounter(0);
                break;
            case "PAN":
                p.setPanCounter(0);
                break;
            case "VOTER":
                p.setVoterIdCounter(0);
                break;
            case "DRIVING":
                p.setDlCounter(0);
                break;
            default:
                return LinkageResponse.error("Invalid source for delete: " + source);
        }

        // If all counters = 0, delete the record
        if (p.getAadhaarCounter() == 0 && p.getPanCounter() == 0 &&
            p.getVoterIdCounter() == 0 && p.getDlCounter() == 0) {
            repo.delete(p);
            return LinkageResponse.success("All sources removed — record deleted completely");
        }

        repo.save(p);
        return LinkageResponse.success("Source deleted successfully", summary(p));
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
    private void initCounters(PersonIdentity p) {
        p.setAadhaarCounter(0);
        p.setPanCounter(0);
        p.setVoterIdCounter(0);
        p.setDlCounter(0);
    }

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

    private String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }

    private String hash(String value) {
        if (value == null) return null;
        return Integer.toHexString(value.hashCode());
    }
}

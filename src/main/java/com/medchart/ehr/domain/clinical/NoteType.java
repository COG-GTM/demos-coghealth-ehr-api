package com.medchart.ehr.domain.clinical;

/**
 * Classification of clinical note content (e.g., progress note, H&amp;P, discharge summary).
 */
public enum NoteType {
    PROGRESS_NOTE,
    HISTORY_AND_PHYSICAL,
    CONSULTATION,
    DISCHARGE_SUMMARY,
    OPERATIVE_NOTE,
    PROCEDURE_NOTE,
    NURSING_NOTE,
    TELEPHONE_ENCOUNTER,
    PATIENT_INSTRUCTION,
    REFERRAL,
    LETTER,
    ADDENDUM,
    OTHER
}

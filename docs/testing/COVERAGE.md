# Business-rule coverage

Final numbers are from `mvn verify` on commit `8bed7cf` (209 tests: 209 passed, 0 failed, 0 skipped). Commits after it only touch `docs/testing/`; `mvn verify` was re-run on the final PR head with identical results. Baseline was measured on `main` (`d123d9c`) with `mvn org.jacoco:jacoco-maven-plugin:0.8.11:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.11:report`.

| Package | Baseline line | Baseline branch | Final line | Final branch |
|---|---|---|---|---|
| `com.medchart.ehr.service` | 0.0% (0/241) | 0.0% (0/37) | 100.0% (260/260) | 98.0% (50/51) |
| `com.medchart.ehr.service.chronic` | 0.0% (0/73) | 0.0% (0/8) | 100.0% (73/73) | 100.0% (8/8) |
| `com.medchart.ehr.mapper` | 0.0% (0/331) | 0.0% (0/153) | 99.7% (330/331) | 97.4% (149/153) |
| `com.medchart.ehr.interop.fhir` | 0.0% (0/273) | 0.0% (0/92) | 99.3% (278/280) | 95.9% (94/98) |
| `com.medchart.ehr.audit` | 0.0% (0/218) | 0.0% (0/24) | 100.0% (218/218) | 95.8% (23/24) |
| `com.medchart.ehr.legacy` | 27.1% (155/572) | 32.6% (58/178) | 95.9% (558/582) | 94.5% (172/182) |

## Reproduce

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B verify   # runs tests, writes target/site/jacoco/jacoco.csv and enforces the 90% line gate
# per-package numbers: target/site/jacoco/jacoco.csv (HTML: target/site/jacoco/index.html, not committed)
```

The `business-rule-coverage-gate` JaCoCo `check` execution in `pom.xml` fails `mvn verify` if any of the six packages drops below `jacoco.line.minimum` (0.90) line coverage. No classes are excluded from coverage.

## Rule -> test traceability

| Rule | Status | Tests |
|---|---|---|
| BR-SVC-01 | covered | `PatientServiceTest#brSvc01_patientAccessMethodsAreAudited`<br>`PatientServiceTest#brSvc01_lookupReturnsMappedDto` |
| BR-SVC-02 | covered | `PatientServiceTest#brSvc02_notFoundMessagesDoNotContainSsn` |
| BR-SVC-03 | covered | `PatientServiceTest#brSvc03_ssnLookupDisabled` |
| BR-SVC-04 | covered | `PatientServiceTest#brSvc04_searchWithoutSortDelegatesToRepository` |
| BR-SVC-05 | defect-fixed | `PatientServiceTest#brSvc05_sortedSearchUsesWhitelistedColumn`<br>`PatientServiceTest#brSvc05_sortWhitelistAcceptsKnownForms`<br>`PatientServiceTest#brSvc05_sortInjectionRejected` |
| BR-SVC-06 | covered | `PatientServiceTest#brSvc06_duplicateMrnRejected`<br>`PatientServiceTest#brSvc06_newMrnKept` |
| BR-SVC-07 | covered | `PatientServiceTest#brSvc07_mrnGeneratedWhenMissing` |
| BR-SVC-08 | covered | `PatientServiceTest#brSvc08_updateAppliesDto`<br>`PatientServiceTest#brSvc08_updateMissingPatientFails` |
| BR-SVC-09 | covered | `AppointmentServiceTest#brSvc09_schedulingIsAudited`<br>`AppointmentServiceTest#brSvc09_eligiblePatientScheduled`<br>`AppointmentServiceTest#brSvc09_zeroAndMissingCopay`<br>`AppointmentServiceTest#brSvc09_ineligiblePatientRejected` |
| BR-SVC-10 | covered | `AppointmentServiceTest#brSvc10_freshCacheHitSkipsGateway`<br>`AppointmentServiceTest#brSvc10_cacheMissCallsGatewayAndCaches`<br>`AppointmentServiceTest#brSvc10_cachedNullAmounts` |
| BR-SVC-11 | defect-fixed | `AppointmentServiceTest#brSvc11_staleCacheReverified`<br>`AppointmentServiceTest#brSvc11_cacheJustInsideTtlServed`<br>`AppointmentServiceTest#brSvc11_missingTimestampIsStale` |
| BR-SVC-12 | defect-fixed | `AppointmentServiceTest#brSvc12_ssnNeverCached` |
| BR-SVC-13 | covered | `AppointmentServiceTest#brSvc13_ineligibleNotCached`<br>`AppointmentServiceTest#brSvc13_eligibleWithoutAmounts` |
| BR-SVC-14 | covered | `InsuranceGatewayTest#brSvc14_structuredEligibilityWithMaskedSsn`<br>`InsuranceGatewayTest#brSvc14_shortIdentifierMasked`<br>`InsuranceGatewayTest#brSvc14_interruptHandled` |
| BR-SVC-15 | covered | `InsuranceGatewayTest#brSvc15_claimLifecycle` |
| BR-SVC-16 | covered | `ProviderNotificationServiceTest#brSvc16_allChannelsSucceed`<br>`ProviderNotificationServiceTest#brSvc16_failureReported`<br>`ProviderNotificationServiceTest#brSvc16_async` |
| BR-SVC-17 | covered | `ProviderNotificationServiceTest#brSvc17_criticalAlertAuditedStub`<br>`ProviderNotificationServiceTest#brSvc17_careTeamStub` |
| BR-SVC-18 | covered | `EncounterServiceTest#brSvc18_createAssignsNumberAndStatus` |
| BR-SVC-19 | covered | `EncounterServiceTest#brSvc19_checkIn`<br>`EncounterServiceTest#brSvc19_start`<br>`EncounterServiceTest#brSvc19_cancel`<br>`EncounterServiceTest#brSvc19_noShow`<br>`EncounterServiceTest#brSvc19_completeNotes`<br>`EncounterServiceTest#brSvc19_unknownEncounterNoOp` |
| BR-SVC-20 | covered | `EncounterServiceTest#brSvc20_scheduleDayWindow`<br>`EncounterServiceTest#brSvc20_dateRangeInclusiveEnd` |
| BR-SVC-21 | covered | `ProviderServiceTest#brSvc21_deactivateSoftDeletes`<br>`ProviderServiceTest#brSvc21_deactivateUnknown`<br>`ProviderServiceTest#brSvc21_searchBindsParameter` |
| BR-SVC-22 | covered | `CustomUserDetailsServiceTest#brSvc22_knownUserLoaded`<br>`CustomUserDetailsServiceTest#brSvc22_unknownUserRejected` |
| BR-SVC-23 | covered | `EncounterServiceTest#brSvc23_encounterDelegation`<br>`EncounterServiceTest#brSvc23_providerAccessMissing`<br>`ProviderServiceTest#brSvc23_providerDelegation` |
| BR-CHR-01 | covered | `MedicationAdherenceTrackerTest#brChr01_pdcZeroForEmptyPeriod`<br>`MedicationAdherenceTrackerTest#brChr01_pdcZeroForReversedPeriod` |
| BR-CHR-02 | open-question | `MedicationAdherenceTrackerTest#brChr02_pdcPositivePeriodIsStub` |
| BR-CHR-03 | covered | `ChronicMedicationServiceTest#brChr03_chronicAccessAudited`<br>`MedicationAdherenceTrackerTest#brChr03_adherenceAccessAudited` |
| BR-CHR-04 | covered | `ChronicMedicationServiceTest#brChr04_chronicStubsThrow`<br>`MedicationAdherenceTrackerTest#brChr04_adherenceStubsThrow`<br>`PharmacyIntegrationServiceTest#brChr04_pharmacyStubsThrow` |
| BR-CHR-05 | covered | `MedicationNotificationServiceTest#brChr05_nonAdherenceAlert`<br>`MedicationNotificationServiceTest#brChr05_alertsAudited` |
| BR-CHR-06 | covered | `MedicationNotificationServiceTest#brChr06_careGapAlert` |
| BR-CHR-07 | covered | `MedicationNotificationServiceTest#brChr07_criticalLabEscalates` |
| BR-CHR-08 | covered | `MedicationNotificationServiceTest#brChr08_emptyDigestSkipped`<br>`MedicationNotificationServiceTest#brChr08_digestEmailedWithTotal`<br>`MedicationNotificationServiceTest#brChr08_eachCategoryTriggers` |
| BR-CHR-09 | covered | `MedicationNotificationServiceTest#brChr09_refillReminderStub` |
| BR-CHR-10 | covered | `ChronicMedicationServiceTest#brChr10_careGapCarrier`<br>`MedicationAdherenceTrackerTest#brChr10_atRiskPatientCarrier` |
| BR-MAP-01 | covered | `FhirPatientMapperTest#brMap01_resourceTypeAndId` |
| BR-MAP-02 | open-question | `FhirPatientMapperTest#brMap02_identifierSystems`<br>`FhirPatientMapperTest#brMap02_missingSsnIsEmpty` |
| BR-MAP-03 | covered | `FhirPatientMapperTest#brMap03_name` |
| BR-MAP-04 | defect-fixed | `FhirPatientMapperTest#brMap04_genderOutbound`<br>`FhirPatientMapperTest#brMap04_nullGender`<br>`FhirPatientMapperTest#brMap04_genderInbound`<br>`FhirPatientMapperTest#brMap04_genderRoundTrip` |
| BR-MAP-05 | covered | `FhirPatientMapperTest#brMap05_birthDate` |
| BR-MAP-06 | covered | `FhirPatientMapperTest#brMap06_telecom` |
| BR-MAP-07 | covered | `FhirPatientMapperTest#brMap07_address` |
| BR-MAP-08 | covered | `FhirPatientMapperTest#brMap08_activeOutbound`<br>`FhirPatientMapperTest#brMap08_activeInbound` |
| BR-MAP-09 | covered | `FhirPatientMapperTest#brMap09_deceased` |
| BR-MAP-10 | covered | `FhirPatientMapperTest#brMap10_maritalStatus`<br>`FhirPatientMapperTest#brMap10_noMaritalStatus` |
| BR-MAP-11 | covered | `FhirPatientMapperTest#brMap11_inboundIdentifiers`<br>`FhirPatientMapperTest#brMap11_inboundSparse` |
| BR-MAP-12 | covered | `PatientMapperTest#brMap12_dtoHasNoSsn`<br>`PatientMapperTest#brMap12_toDto`<br>`PatientMapperTest#brMap12_toEntityIgnoresServerFields` |
| BR-MAP-13 | covered | `PatientMapperTest#brMap13_partialUpdate`<br>`PatientMapperTest#brMap13_fullUpdate` |
| BR-MAP-14 | covered | `PatientMapperTest#brMap14_nullsMapToNull`<br>`PatientMapperTest#brMap14_sparsePatient` |
| BR-FHIR-01 | covered | `FhirR4ExportServiceTest#brFhir01_matchedRowExportsFullBundle` |
| BR-FHIR-02 | covered | `FhirR4ExportServiceTest#brFhir02_unmatchedRowPassthrough` |
| BR-FHIR-03 | defect-fixed | `FhirR4ExportServiceTest#brFhir03_newYorkOffsetPerDate` |
| BR-FHIR-04 | covered | `FhirR4ExportServiceTest#brFhir04_unparseablePtoKeptVerbatim` |
| BR-FHIR-05 | covered | `FhirR4ExportServiceTest#brFhir05_noPtoNoSlot` |
| BR-FHIR-06 | covered | `FhirR4ContractValidatorTest#brFhir06_practitionerRequiresNpi`<br>`FhirR4ContractValidatorTest#brFhir06_endToEndValidation` |
| BR-FHIR-07 | covered | `FhirR4ContractValidatorTest#brFhir07_roleRequiresOrganization`<br>`FhirR4ContractValidatorTest#brFhir06_endToEndValidation` |
| BR-FHIR-08 | covered | `FhirR4ContractValidatorTest#brFhir08_slotRequiresOffset`<br>`FhirR4ContractValidatorTest#brFhir06_endToEndValidation` |
| BR-FHIR-09 | covered | `FhirR4ContractValidatorTest#brFhir09_invalidJsonFatal` |
| BR-FHIR-10 | covered | `FhirR4ContractValidatorTest#brFhir10_findingSeverity` |
| BR-FHIR-11 | covered | `InterfaceContractCheckTest#brFhir11_cleanExportPasses`<br>`InterfaceContractCheckTest#brFhir11_errorsFail`<br>`InterfaceContractCheckTest#brFhir11_unwritableReportDir`<br>`InterfaceContractCheckTest#brFhir11_defaultReportDir` |
| BR-FHIR-12 | covered | `InterfaceContractCheckTest#brFhir12_unknownCostCenterFails` |
| BR-FHIR-13 | defect-fixed | `InterfaceContractCheckTest#brFhir13_duplicateByName`<br>`InterfaceContractCheckTest#brFhir13_duplicateByNpi` |
| BR-AUD-01 | covered | `AuditAspectTest#brAud01_eventFromAnnotation` |
| BR-AUD-02 | covered | `AuditAspectTest#brAud02_firstLongIsPatientId`<br>`AuditAspectTest#brAud02_noLongArgument` |
| BR-AUD-03 | covered | `AuditAspectTest#brAud03_successRecorded` |
| BR-AUD-04 | covered | `AuditAspectTest#brAud04_failureRecordedAndRethrown` |
| BR-AUD-05 | covered | `AuditAspectTest#brAud05_forwardedForAndUserAgent`<br>`AuditAspectTest#brAud05_remoteAddrFallback`<br>`AuditAspectTest#brAud05_noRequestContext`<br>`AuditAspectTest#brAud05_requestErrorsTolerated` |
| BR-AUD-06 | covered | `AuditServiceTest#brAud06_eventPersisted`<br>`AuditServiceTest#brAud06_persistenceFailureSwallowed`<br>`AuditServiceTest#brAud06_asyncRequiresNew` |
| BR-AUD-07 | covered | `PatientAccessLoggerTest#brAud07_accessRecorded` |
| BR-AUD-08 | covered | `PatientAccessLoggerTest#brAud08_failedAccessRecorded` |
| BR-AUD-09 | covered | `PatientAccessLoggerTest#brAud09_bulkAccessRecorded`<br>`PatientAccessLoggerTest#brAud09_zeroRecordBulk` |
| BR-AUD-10 | covered | `PatientAccessLoggerTest#brAud10_accessReportStub` |
| BR-AUD-11 | covered | `MedicationAuditLoggerTest#brAud11_controlledPrescription`<br>`MedicationAuditLoggerTest#brAud11_nonControlledPrescription` |
| BR-AUD-12 | covered | `MedicationAuditLoggerTest#brAud12_modification`<br>`MedicationAuditLoggerTest#brAud12_cancellation` |
| BR-AUD-13 | covered | `MedicationAuditLoggerTest#brAud13_eprescribe`<br>`MedicationAuditLoggerTest#brAud13_nullStatus` |
| BR-AUD-14 | covered | `MedicationAuditLoggerTest#brAud14_pdmp`<br>`MedicationAuditLoggerTest#brAud14_medicationHistory` |
| BR-AUD-15 | covered | `AuditServiceTest#brAud15_defaultsAndVocabulary` |
| BR-LEG-01 | covered | `InsuranceCacheTest#brLeg01_cachePerPatientAndPayer`<br>`InsuranceCacheTest#brLeg01_cacheTimeIsInstant`<br>`InsuranceCacheTest#brLeg01_recacheReplaces` |
| BR-LEG-02 | covered | `InsuranceCacheTest#brLeg02_clearPatientOnly` |
| BR-LEG-03 | open-question | `LegacyPatientLookupTest#brLeg03_mrnLookupBound`<br>`LegacyPatientLookupTest#brLeg03_notFoundReturnsNull`<br>`LegacyPatientLookupTest#brLeg03_ssnLookupBound` |
| BR-LEG-04 | covered | `LegacyPatientLookupTest#brLeg04_containsSearchBound`<br>`LegacyPatientLookupTest#brLeg04_rawSearch` |
| BR-LEG-05 | covered | `LegacyPatientLookupTest#brLeg05_demographics` |
| BR-LEG-06 | defect-fixed | `ReportGeneratorTest#brLeg06_encounterSummaryColumns` |
| BR-LEG-07 | covered | `EncounterExportServiceTest#brLeg07_dateRangeCsv`<br>`EncounterExportServiceTest#brLeg07_emptyRange` |
| BR-LEG-08 | defect-fixed | `EncounterExportServiceTest#brLeg08_historyMasksSsn`<br>`EncounterExportServiceTest#brLeg08_maskEdgeCases` |
| BR-LEG-09 | defect-fixed | `ReportGeneratorTest#brLeg09_dailyReportCleansTempFile`<br>`ReportGeneratorTest#brLeg09_rosterFilesAreUnique` |
| BR-LEG-10 | defect-fixed | `ReportGeneratorTest#brLeg10_departmentProductivity` |
| BR-LEG-11 | covered | `HrPtoSyncJobRulesTest#brLeg11_unknownCostCenterRejected` |
| BR-LEG-12 | covered | `HrPtoSyncJobRulesTest#brLeg12_skipNoPtoOrUnapproved` |
| BR-LEG-13 | covered | `HrPtoSyncJobRulesTest#brLeg13_messagePerBlockDay` |
| BR-LEG-14 | covered | `HrPtoSyncJobRulesTest#brLeg14_noDatasource`<br>`HrPtoSyncJobRulesTest#brLeg14_blockRowMapping` |
| BR-LEG-15 | covered | `HrPtoSyncJobRulesTest#brLeg15_runLog`<br>`HrPtoSyncJobRulesTest#brLeg15_malformedLogLines`<br>`HrPtoSyncJobRulesTest#brLeg15_unwritableOutputTolerated` |
| BR-LEG-16 | covered | `HrPtoSyncJobRulesTest#brLeg16_runOnceAndNightly` |
| BR-LEG-17 | covered | `HrPtoSyncJobRulesTest#brLeg17_parsePtoDays` |
| BR-LEG-18 | covered | `HrPtoSyncJobRulesTest#brLeg18_siuOptionalSegments` |
| BR-LEG-19 | covered | `HrExportReaderRulesTest#brLeg19_directoryOverride`<br>`HrExportReaderRulesTest#brLeg19_noExports`<br>`HrExportReaderRulesTest#brLeg19_parsingRules`<br>`HrExportReaderRulesTest#brLeg19_missingClasspathExport` |
| BR-LEG-20 | covered | `DepartmentMappingRulesTest#brLeg20_lookupRules`<br>`DepartmentMappingRulesTest#brLeg20_namesAndKnownSet` |
| BR-LEG-21 | covered | `HrPtoSyncJobRulesTest#brLeg21_recordRules` |
| BR-LEG-22 | covered | `EncounterExportServiceTest#brLeg22_allPatientsFileExport`<br>`EncounterExportServiceTest#brLeg22_unwritableExportFails` |

## Defects found

| Rule | Symptom | Fix |
|---|---|---|
| BR-SVC-05 | searchPatients concatenated the caller's sort into native SQL (SQL injection; ORDER BY ssn possible); invalid sort also only failed after the count query ran. | Allow-list sort columns/directions in PatientService.toOrderBy and validate before any query. |
| BR-SVC-11 | Cached eligibility never expired (AGENTS.md: 'cache result with TTL'). | AppointmentService.ELIGIBILITY_TTL = 24h measured on Instant (InsuranceCache.cachedAt is now an Instant, so DST changes do not stretch the TTL); stale/undated entries are re-verified. |
| BR-SVC-12 | AppointmentService cached the patient SSN in InsuranceCache (AGENTS.md gap #4). | Pass null for SSN when caching eligibility. |
| BR-LEG-08 | Encounter history export printed the full SSN. | EncounterExportService.maskSsn -> XXX-XX-<last4>. |
| BR-LEG-10 | Department productivity report concatenated department names into SQL. | Bind :department as a parameter. |
| BR-LEG-09 | generateDailyReport left the PHI roster temp file on disk (AGENTS.md gap #5). | Delete the temp file in a finally block; roster file names get a UUID suffix so same-second requests never share (or delete) a file. |
| BR-LEG-06 | Encounter summary printed SSN under 'DOB:' and DOB/first-name under 'Provider:' (column index off by one). | Use row[9] for DOB and row[10]/row[11] for provider. |
| BR-FHIR-13 | Duplicate practitioner check ignored the same NPI under different names/HR ids. | Also group records by trimmed NPI in InterfaceContractCheck. |
| BR-FHIR-03 | Slot instants used a hard-coded -04:00 offset, wrong for EST dates (Nov-Mar). | Convert with ZoneId America/New_York per date. |
| BR-MAP-04 | Unrecognised/'unknown' FHIR gender mapped to Gender.OTHER, so 'unknown' did not round-trip. | Map default to Gender.UNKNOWN. |

## Open questions

- **BR-CHR-02**: PDC calculation for a real period is still a stub (AGENTS.md lists it as a task); tests assert the stub and the documented thresholds are not yet enforceable.
- **BR-MAP-02**: FhirPatientMapper always emits a us-ssn identifier (empty value when unknown). Should SSN be exported in FHIR at all, and should the empty identifier be omitted?
- **BR-LEG-03**: LegacyPatientLookup (and EncounterExportService batch exports, gap #2) bypass @AuditAccess (AGENTS.md gap #3). Adding audit needs a decision on acting-user context for batch callers; tests document current behaviour.
- **BR-LEG-15**: HrPtoSyncJob only logs outbound/error-queue write failures, so a message can be lost while the run reports it as sent.
- **DOCS**: AGENTS.md requires docs/PHI_DATA_FLOW.md and HIPAA_COMPLIANCE.md, but neither exists in the repo; rules were mined from AGENTS.md, code and docs/interfaces instead.
- **BR-LEG-09**: generatePatientRoster/generateEncounterSummary still return temp-file paths containing PHI (cleanup is the caller's responsibility); only generateDailyReport cleans up.

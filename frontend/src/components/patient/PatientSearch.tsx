import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, User, X } from 'lucide-react';
import { usePatientSearch } from '../../hooks';
import { formatPatientName, formatDate, formatGender, calculateAge, cn } from '../../utils';
import type { Patient } from '../../types';

interface PatientSearchProps {
  onSelect?: (patient: Patient) => void;
  autoFocus?: boolean;
  placeholder?: string;
}

export function PatientSearch({ onSelect, autoFocus, placeholder = 'Search patients...' }: PatientSearchProps) {
  const [query, setQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const { data: patients, isLoading } = usePatientSearch(query);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelect = (patient: Patient) => {
    if (onSelect) {
      onSelect(patient);
    } else {
      navigate(`/patients/${patient.id}`);
    }
    setQuery('');
    setIsOpen(false);
  };

  return (
    <div ref={containerRef} className="relative">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          placeholder={placeholder}
          autoFocus={autoFocus}
          className="input pl-10 pr-10"
        />
        {query && (
          <button
            aria-label="Clear search"
            onClick={() => {
              setQuery('');
              inputRef.current?.focus();
            }}
            className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-slate-400 hover:text-slate-600"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {isOpen && query.length >= 2 && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-lg z-50 max-h-80 overflow-auto">
          {isLoading ? (
            <div className="p-4 text-center text-sm text-slate-500">Searching...</div>
          ) : patients && patients.length > 0 ? (
            <ul>
              {patients.map((patient) => (
                <li key={patient.id}>
                  <button
                    onClick={() => handleSelect(patient)}
                    className="w-full flex items-center gap-3 p-3 hover:bg-slate-50 text-left transition-colors"
                  >
                    <div className="w-10 h-10 bg-slate-100 rounded-full flex items-center justify-center flex-shrink-0">
                      <User className="w-5 h-5 text-slate-400" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="font-medium text-sm truncate">
                        {formatPatientName(patient)}
                      </div>
                      <div className="text-xs text-slate-500">
                        {formatGender(patient.gender)}, {calculateAge(patient.dateOfBirth)}y | 
                        DOB: {formatDate(patient.dateOfBirth)} | 
                        MRN: {patient.mrn}
                      </div>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <div className="p-4 text-center text-sm text-slate-500">
              No patients found for "{query}"
            </div>
          )}
        </div>
      )}
    </div>
  );
}

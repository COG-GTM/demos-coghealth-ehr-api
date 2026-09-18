import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Search,
  Bell,
  Inbox,
  ChevronDown,
  LogOut,
  Settings,
  User,
  Menu,
  FileText,
  FlaskConical,
  CheckSquare,
} from 'lucide-react';
import { cn } from '../../utils';

interface HeaderProps {
  onMenuClick: () => void;
}

export function Header({ onMenuClick }: HeaderProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [showSearch, setShowSearch] = useState(false);
  const [showInbox, setShowInbox] = useState(false);
  const [showProfile, setShowProfile] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);
  const inboxRef = useRef<HTMLDivElement>(null);
  const profileRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const inboxCounts = { results: 5, messages: 4, tasks: 3 };

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) {
        setShowSearch(false);
      }
      if (inboxRef.current && !inboxRef.current.contains(event.target as Node)) {
        setShowInbox(false);
      }
      if (profileRef.current && !profileRef.current.contains(event.target as Node)) {
        setShowProfile(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const query = searchQuery.trim();
    if (query) {
      navigate(`/patients?q=${encodeURIComponent(query)}`);
      setShowSearch(false);
      setSearchQuery('');
    }
  };

  const totalInbox = inboxCounts.results + inboxCounts.messages + inboxCounts.tasks;

  return (
    <header className="h-14 bg-white border-b border-slate-200 flex items-center px-4 gap-4 sticky top-0 z-30">
      <button
        onClick={onMenuClick}
        className="lg:hidden p-2 -ml-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-md"
      >
        <Menu className="w-5 h-5" />
      </button>

      <Link to="/" className="flex items-center gap-2 font-semibold text-primary-600">
        <div className="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center">
          <span className="text-white text-sm font-bold">M</span>
        </div>
        <span className="hidden sm:inline">MedChart EHR</span>
      </Link>

      <div ref={searchRef} className="flex-1 max-w-xl relative">
        <form onSubmit={handleSearch}>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              type="text"
              placeholder="Search patients (Ctrl+K)"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => setShowSearch(true)}
              className="w-full pl-10 pr-4 py-2 bg-slate-100 border-0 rounded-lg text-sm placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:ring-primary-500 transition-colors"
            />
          </div>
        </form>

        {showSearch && searchQuery.length >= 2 && (
          <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-lg overflow-hidden">
            <div className="p-2 text-xs text-slate-500 bg-slate-50 border-b">
              Press Enter to search for "{searchQuery}"
            </div>
            <div className="p-4 text-sm text-slate-500 text-center">
              Type to search patients by name, MRN, or DOB
            </div>
          </div>
        )}
      </div>

      <div className="flex items-center gap-2">
        <div ref={inboxRef} className="relative">
          <button
            onClick={() => setShowInbox(!showInbox)}
            className={cn(
              'relative p-2 rounded-lg transition-colors',
              showInbox ? 'bg-slate-100 text-slate-900' : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100'
            )}
          >
            <Inbox className="w-5 h-5" />
            {totalInbox > 0 && (
              <span className="absolute -top-1 -right-1 w-5 h-5 bg-danger-500 text-white text-xs font-medium rounded-full flex items-center justify-center">
                {totalInbox > 9 ? '9+' : totalInbox}
              </span>
            )}
          </button>

          {showInbox && (
            <div className="absolute right-0 top-full mt-2 w-80 bg-white border border-slate-200 rounded-lg shadow-lg overflow-hidden">
              <div className="p-3 border-b bg-slate-50 font-medium text-sm">Inbox</div>
              <div className="divide-y max-h-96 overflow-auto">
                <Link
                  to="/inbox/results"
                  className="flex items-center gap-3 p-3 hover:bg-slate-50 transition-colors"
                  onClick={() => setShowInbox(false)}
                >
                  <div className="w-8 h-8 bg-danger-100 text-danger-600 rounded-full flex items-center justify-center">
                    <FlaskConical className="w-4 h-4" />
                  </div>
                  <div className="flex-1">
                    <div className="text-sm font-medium">Results</div>
                    <div className="text-xs text-slate-500">{inboxCounts.results} pending review</div>
                  </div>
                  <span className="badge badge-danger">{inboxCounts.results}</span>
                </Link>
                <Link
                  to="/inbox/messages"
                  className="flex items-center gap-3 p-3 hover:bg-slate-50 transition-colors"
                  onClick={() => setShowInbox(false)}
                >
                  <div className="w-8 h-8 bg-primary-100 text-primary-600 rounded-full flex items-center justify-center">
                    <FileText className="w-4 h-4" />
                  </div>
                  <div className="flex-1">
                    <div className="text-sm font-medium">Messages</div>
                    <div className="text-xs text-slate-500">{inboxCounts.messages} unread</div>
                  </div>
                  <span className="badge badge-info">{inboxCounts.messages}</span>
                </Link>
                <Link
                  to="/inbox/tasks"
                  className="flex items-center gap-3 p-3 hover:bg-slate-50 transition-colors"
                  onClick={() => setShowInbox(false)}
                >
                  <div className="w-8 h-8 bg-warning-100 text-warning-600 rounded-full flex items-center justify-center">
                    <CheckSquare className="w-4 h-4" />
                  </div>
                  <div className="flex-1">
                    <div className="text-sm font-medium">Tasks</div>
                    <div className="text-xs text-slate-500">{inboxCounts.tasks} pending</div>
                  </div>
                  <span className="badge badge-warning">{inboxCounts.tasks}</span>
                </Link>
              </div>
            </div>
          )}
        </div>

        <div ref={profileRef} className="relative">
          <button
            onClick={() => setShowProfile(!showProfile)}
            className={cn(
              'flex items-center gap-2 p-2 rounded-lg transition-colors',
              showProfile ? 'bg-slate-100' : 'hover:bg-slate-100'
            )}
          >
            <div className="w-8 h-8 bg-primary-100 text-primary-600 rounded-full flex items-center justify-center">
              <User className="w-4 h-4" />
            </div>
            <span className="hidden md:inline text-sm font-medium">Dr. Chen</span>
            <ChevronDown className="w-4 h-4 text-slate-400" />
          </button>

          {showProfile && (
            <div className="absolute right-0 top-full mt-2 w-56 bg-white border border-slate-200 rounded-lg shadow-lg overflow-hidden">
              <div className="p-3 border-b">
                <div className="font-medium text-sm">Dr. Sarah Chen</div>
                <div className="text-xs text-slate-500">Internal Medicine</div>
              </div>
              <div className="p-1">
                <Link
                  to="/settings"
                  className="flex items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100 rounded-md"
                  onClick={() => setShowProfile(false)}
                >
                  <Settings className="w-4 h-4" />
                  Settings
                </Link>
                <button
                  onClick={() => {
                    setShowProfile(false);
                    localStorage.removeItem('auth_token');
                    navigate('/login');
                  }}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100 rounded-md"
                >
                  <LogOut className="w-4 h-4" />
                  Sign out
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

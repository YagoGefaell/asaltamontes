import { useEffect, useState } from "react";
import UserCard from "../../shared/ui/UserCard";
import "./Search.css";
import { useUserProfile } from "../../features/users/hooks/useUserProfile";

const MAX_RESULTS = 20;

const Search = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const { searchUsersByUsername } = useUserProfile();

  const handleSearch = async (e) => {
    const query = e.target.value.trim();
    setSearchTerm(query);
    if (query.length === 0 || query.length < 2) {
      setSearchResults([]);
      return;
    }
    try {
      const results = await searchUsersByUsername(query);
      setSearchResults(results.slice(0, MAX_RESULTS));
    } catch (err) {
      console.error("Error searching users:", err);
      setSearchResults([]);
    }
  };

  return (
    <div className="search-page">
      <div className="search-input-container">
        <input
          type="text"
          placeholder="Buscar"
          value={searchTerm}
          onChange={handleSearch}
        />
      </div>

      <div className="search-results">
        {searchResults.length > 0 ? (
          searchResults.map((user, index) => <UserCard key={index} {...user} />)
        ) : (
          <p className="no-results">No se encontraron usuarias</p>
        )}
      </div>
    </div>
  );
};

export default Search;

import React, { useState } from "react";
import UserCard from "../../shared/components/UserCard";
import "./Search.css";

const MAX_RESULTS = 20;

const Search = () => {
  const [searchTerm, setSearchTerm] = useState("");

  const users = [
    { name: "Ana López", username: "ana_lopez", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Ana" },
    { name: "Laura García", username: "laura_g", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Laura" },
    { name: "Marta Pérez", username: "marta_p", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Marta" },
    { name: "Sofía Ruiz", username: "sofia_r", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Sofia" },
    { name: "Lucía Torres", username: "lucia_t", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Lucia" },
    { name: "Carmen Díaz", username: "carmen_d", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Carmen" },
    { name: "Isabel Morales", username: "isabel_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Isabel" },
    { name: "Patricia Sánchez", username: "patricia_s", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Patricia" },
    { name: "María Jiménez", username: "maria_j", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Maria" },
    { name: "Elena Fernández", username: "elena_f", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Elena" },
    { name: "Beatriz Ruiz", username: "beatriz_r", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Beatriz" },
    { name: "Verónica Morales", username: "veronica_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Veronica" },
    { name: "Daniela Gómez", username: "daniela_g", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Daniela" },
    { name: "Raquel Herrera", username: "raquel_h", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Raquel" },
    { name: "Claudia Navarro", username: "claudia_n", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Claudia" },
    { name: "Nuria Castro", username: "nuria_c", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Nuria" },
    { name: "Irene Ramos", username: "irene_r", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Irene" },
    { name: "Sandra Molina", username: "sandra_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Sandra" },
    { name: "Mónica Ortega", username: "monica_o", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Monica" },
    { name: "Lorena Vega", username: "lorena_v", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Lorena" },
    { name: "Paula Delgado", username: "paula_d", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Paula" },
    { name: "Rosa Castillo", username: "rosa_c", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Rosa" },
    { name: "Teresa Lozano", username: "teresa_l", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Teresa" },
    { name: "Gloria Alonso", username: "gloria_a", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Gloria" },
    { name: "Yolanda Cordero", username: "yolanda_c", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Yolanda" },
    { name: "Julia Serrano", username: "julia_s", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Julia" },
    { name: "Carolina Domínguez", username: "carolina_d", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Carolina" },
    { name: "Margarita Fuentes", username: "margarita_f", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Margarita" },
    { name: "Adriana Campos", username: "adriana_c", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Adriana" },
    { name: "Susana Rivas", username: "susana_r", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Susana" },
    { name: "Paola Marín", username: "paola_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Paola" },
    { name: "Marina León", username: "marina_l", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Marina" },
    { name: "Cristina Blanco", username: "cristina_b", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Cristina" },
    { name: "Esther Vidal", username: "esther_v", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Esther" },
    { name: "Noelia Soler", username: "noelia_s", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Noelia" },
    { name: "Raquel Méndez", username: "raquel_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=RaquelM" },
    { name: "Victoria Ramos", username: "victoria_r", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Victoria" },
    { name: "Lorena Pardo", username: "lorena_p", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=LorenaP" },
    { name: "Natalia Herrera", username: "natalia_h", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Natalia" },
    { name: "Vanessa Prieto", username: "vanessa_p", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Vanessa" },
    { name: "Patricia Molina", username: "patricia_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=PatriciaM" },
    { name: "Lorena Vega", username: "lorena_v2", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=LorenaV" },
    { name: "Alicia Martín", username: "alicia_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Alicia" },
    { name: "Clara Jiménez", username: "clara_j", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Clara" },
    { name: "Maribel Santos", username: "maribel_s", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Maribel" },
    { name: "Olga Cabrera", username: "olga_c", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Olga" },
    { name: "Rocío Arias", username: "rocio_a", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Rocio" },
    { name: "Celia Marín", username: "celia_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Celia" },
    { name: "Mireia Torres", username: "mireia_t", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Mireia" },
    { name: "Fátima Ortega", username: "fatima_o", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Fatima" },
    { name: "Inés Cabrera", username: "ines_c", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Ines" },
    { name: "Elisa Bravo", username: "elisa_b", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Elisa" },
    { name: "Daniela Paredes", username: "daniela_p", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=DanielaP" },
    { name: "Carla Montoya", username: "carla_m", avatar: "https://api.dicebear.com/7.x/avataaars/svg?seed=Carla" },
    ];

  const filteredUsers = users.filter(
    (user) =>
      user.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      user.username.toLowerCase().includes(searchTerm.toLowerCase())
  ).slice(0, MAX_RESULTS);

  return (
    <div className="search-page">
      <div className="search-input-container">
        <input
          type="text"
          placeholder="Buscar"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>

      <div className="search-results">
        {filteredUsers.length > 0 ? (
          filteredUsers.map((user, index) => <UserCard key={index} {...user} />)
        ) : (
          <p className="no-results">No se encontraron usuarias</p>
        )}
      </div>
    </div>
  );
};

export default Search;

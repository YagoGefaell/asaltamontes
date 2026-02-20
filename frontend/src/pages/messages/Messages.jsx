import "./Messages.css";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

const Messages = () => {
  const navigate = useNavigate();

  const chats = [
    { id: 1, name: "María García", lastMsg: "Hola! ¿Cómo estás?", avatar: "👩" },
    { id: 2, name: "Laura Martínez", lastMsg: "Nos vemos mañana!", avatar: "👱‍♀️" },
    { id: 3, name: "Sofía López", lastMsg: "Jajaja muy bueno!", avatar: "👩‍🦱" },
  ];

  const [selectedChat, setSelectedChat] = useState(null);

  useEffect(() => {
    const className = "chat-open";

    if (selectedChat) {
      document.body.classList.add(className);
    } else {
      document.body.classList.remove(className);
    }

    return () => {
      document.body.classList.remove(className);
    };
  }, [selectedChat]);

  return (
    <div className="messages-page">
      {!selectedChat && (
        <>
          <div className="messages-header">
            <button
              className="back-button"
              type="button"
              aria-label="Volver"
              onClick={() => navigate(-1)}
            >
              ←
            </button>
            <h2 className="messages-title">Mensajes</h2>
          </div>

          <div className="contacts-list">
            {chats.map((chat) => (
              <button
                key={chat.id}
                type="button"
                className="contact-item"
                onClick={() => setSelectedChat(chat)}
              >
                <div className="contact-avatar" aria-hidden="true">
                  {chat.avatar}
                </div>
                <div className="contact-info">
                  <span className="contact-name">{chat.name}</span>
                  <span className="contact-preview">{chat.lastMsg}</span>
                </div>
              </button>
            ))}
          </div>
        </>
      )}

      {selectedChat && (
        <>
          <div className="messages-header">
            <button
              className="back-button"
              type="button"
              aria-label="Volver"
              onClick={() => setSelectedChat(null)}
            >
              ←
            </button>
            <div className="chat-title">
              <div className="chat-avatar" aria-hidden="true">
                {selectedChat.avatar}
              </div>
              <div className="chat-title-text">
                <span className="chat-name">{selectedChat.name}</span>
                <span className="chat-status">En linea</span>
              </div>
            </div>
          </div>

          <div className="messages">
            <div className="message sent">Hola! ¿Que tal?</div>
            <div className="message received">Hola! Muy bien, ¿y tu?</div>
            <div className="message sent">Genial, ¿nos vemos pronto?</div>
          </div>

          <div className="message-input">
            <input type="text" placeholder="Escribe un mensaje..." />
            <button>Enviar</button>
          </div>
        </>
      )}
    </div>
  );
};

export default Messages;

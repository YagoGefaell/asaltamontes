// UserCard.jsx
import React, { useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import "./UserCard.css";

const MIN_PRESS_DURATION = 150; // duración mínima en ms

const UserCard = ({ name, username, avatar }) => {
  const [pressed, setPressed] = useState(false);
  const pressStartTime = useRef(0);
  const timeoutRef = useRef(null);
  const navigate = useNavigate();

  const handlePressStart = () => {
    pressStartTime.current = Date.now();
    setPressed(true);
  };

  const handlePressEnd = () => {
    const elapsed = Date.now() - pressStartTime.current;
    const remaining = MIN_PRESS_DURATION - elapsed;

    const finish = () => {
      setPressed(false)
      navigate(`/users/${username}`);
    };

    if (remaining > 0) {
      // asegurar duración mínima
      clearTimeout(timeoutRef.current);
      timeoutRef.current = setTimeout(finish, remaining);
    } else {
      setPressed(false);
    }
  };

  return (
    <div
      className={`user-card ${pressed ? "pressed" : ""}`}
      onMouseDown={handlePressStart}
      onMouseUp={handlePressEnd}
      onMouseLeave={handlePressEnd}
      onTouchStart={handlePressStart}
      onTouchEnd={handlePressEnd}
      onTouchCancel={handlePressEnd}
    >
      <img src={avatar} alt={username} className="user-avatar" />
      <div className="user-info">
        <span className="user-name">{name}</span>
        <span className="user-username">@{username}</span>
      </div>
    </div>
  );
};

export default UserCard;

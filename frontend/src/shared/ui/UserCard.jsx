// UserCard.jsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { formatters } from "../../utils/helpers";
import Button from "../forms/Button";
import "./UserCard.css";

const UserCard = ({ 
  fullName, 
  username, 
  profilePictureUrl, 
  showFollowButton = false,
  isFollowing = false,
  onFollowToggle = null,
  followLoading = false 
}) => {
  const [pressed, setPressed] = useState(false);
  const navigate = useNavigate();

  // Usar función utilitaria para generar avatar
  const avatarSrc = formatters.avatar(profilePictureUrl, fullName, username);

  const handleCardClick = (e) => {
    // Don't navigate if clicking on a button or inside button container
    const target = e.target;
    if (target.closest('button') || target.closest('.user-card-action')) {
      return;
    }
    
    navigate(`/users/${username}`);
  };

  const handlePressStart = (e) => {
    // Don't activate press effect on button area
    if (e.target.closest('button') || e.target.closest('.user-card-action')) {
      return;
    }
    setPressed(true);
  };

  const handlePressEnd = (e) => {
    setPressed(false);
  };

  const handleFollowClick = (e) => {
    e.stopPropagation();
    if (onFollowToggle && !followLoading) {
      onFollowToggle(username);
    }
  };

  return (
    <div
      className={`user-card ${pressed ? "pressed" : ""}`}
      onClick={handleCardClick}
      onMouseDown={handlePressStart}
      onMouseUp={handlePressEnd}
      onMouseLeave={handlePressEnd}
    >
      <img src={avatarSrc} alt={username} className="user-avatar" />
      <div className="user-info">
        <span className="user-name">{fullName || username}</span>
        <span className="user-username">@{username}</span>
      </div>
      {showFollowButton && (
        <div className="user-card-action">
          <Button
            variant={isFollowing ? "outline" : "primary"}
            size="small"
            onClick={handleFollowClick}
            disabled={followLoading}
          >
            {followLoading ? '...' : (isFollowing ? 'Siguiendo' : 'Seguir')}
          </Button>
        </div>
      )}
    </div>
  );
};

export default UserCard;

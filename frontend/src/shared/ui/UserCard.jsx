// UserCard.jsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../forms/Button";
import "./UserCard.css";

const UserCard = ({ 
  fullName, 
  username, 
  profilePictureUrl, 
  showFollowButton = false,
  isFollowing = false,
  onFollowToggle = null,
  followLoading = false,
  showChatButton = false,
  onChatClick = null,
  chatLoading = false,
}) => {
  const [pressed, setPressed] = useState(false);
  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const navigate = useNavigate();
  const initial = (username || "?").trim().charAt(0).toUpperCase() || "?";
  const shouldShowImage = Boolean(profilePictureUrl) && !avatarLoadError;

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

  const handlePressEnd = () => {
    setPressed(false);
  };

  const handleFollowClick = (e) => {
    e.stopPropagation();
    if (onFollowToggle && !followLoading) {
      onFollowToggle(username);
    }
  };

  const handleChatClick = (e) => {
    e.stopPropagation();
    if (onChatClick && !chatLoading) {
      onChatClick(username);
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
      {shouldShowImage ? (
        <img
          src={profilePictureUrl}
          alt={username}
          className="user-avatar"
          onError={() => setAvatarLoadError(true)}
        />
      ) : (
        <div className="user-avatar user-avatar-fallback" aria-hidden="true">
          {initial}
        </div>
      )}
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
      {!showFollowButton && showChatButton && (
        <div className="user-card-action">
          <Button
            variant="primary"
            size="small"
            onClick={handleChatClick}
            disabled={chatLoading}
          >
            {chatLoading ? "..." : "Abrir chat"}
          </Button>
        </div>
      )}
    </div>
  );
};

export default UserCard;

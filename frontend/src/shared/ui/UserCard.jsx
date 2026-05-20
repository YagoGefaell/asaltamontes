// UserCard.jsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import FollowUserButton from "../components/FollowUserButton";
import Button from "../forms/Button";
import { formatters } from "../../utils/helpers";
import "./UserCard.css";

const UserCard = ({ 
  fullName, 
  username, 
  profilePictureUrl, 
  showFollowButton = false,
  isFollowing = false,
  onFollowToggle = null,
  showChatButton = false,
  onChatClick = null,
  chatLoading = false,
}) => {
  const [pressed, setPressed] = useState(false);
  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const navigate = useNavigate();
  const avatarUrl = formatters.avatar(profilePictureUrl, fullName, username);
  const initial = formatters.getInitial(fullName, username);

  const handleCardClick = (e) => {
    // Don't navigate if clicking on a button or inside button container
    const target = e.target;
    if (target.closest('button') || target.closest('.user-card-action')) {
      return;
    }
    
    navigate(`/users/${username}`);
  };

  const handlePressStart = (e) => {
    if (e.target.closest('button') || e.target.closest('.user-card-action')) {
      return;
    }
    setPressed(true);
  };

  const handlePressEnd = () => {
    setPressed(false);
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
      {!avatarLoadError ? (
        <img
          src={avatarUrl}
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
          <FollowUserButton
            userId={username}
            initialIsFollowing={isFollowing}
            onToggle={onFollowToggle}
            followLabel="Seguir"
            unfollowLabel="Siguiendo"
          />
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

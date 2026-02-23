import { useState, useCallback } from 'react';
import { useAuth } from '../../auth/hooks/useAuth.js';
import { 
  followUserRequest, 
  unfollowUserRequest
} from '../services/users.service.js';

export function useFollow() {
  const { incrementFollowing, decrementFollowing } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [followersCount, setFollowersCount] = useState(0);
  const [followingCount, setFollowingCount] = useState(0);
  const [isFollowing, setIsFollowing] = useState(false);

  const followUser = useCallback(async (username) => {
    setLoading(true);
    setError(null);
    
    try {
      await followUserRequest(username);
      setIsFollowing(true);
      setFollowersCount(prev => prev + 1);
      // Update current user's following count
      incrementFollowing();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setLoading(false);
    }
  }, [incrementFollowing]);

  const unfollowUser = useCallback(async (username) => {
    setLoading(true);
    setError(null);
    
    try {
      await unfollowUserRequest(username);
      setIsFollowing(false);
      setFollowersCount(prev => Math.max(0, prev - 1));
      // Update current user's following count
      decrementFollowing();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setLoading(false);
    }
  }, [decrementFollowing]);

  const toggleFollow = useCallback(async (username) => {
    if (isFollowing) {
      return await unfollowUser(username);
    } else {
      return await followUser(username);
    }
  }, [isFollowing, followUser, unfollowUser]);

  const resetError = useCallback(() => {
    setError(null);
  }, []);

  const setFollowStatus = useCallback((status) => {
    setIsFollowing(status);
  }, []);

  // Initialize the follow state for a specific user from backend data
  const initializeFollowState = useCallback((userProfile) => {
    if (userProfile) {
      setFollowersCount(userProfile.followersCount || 0);
      setFollowingCount(userProfile.followingCount || 0);
      // Use isFollowing from the UserProfileDto
      setIsFollowing(userProfile.isFollowing || false);
    }
  }, []);

  return {
    loading,
    error,
    followersCount,
    followingCount,
    isFollowing,
    followUser,
    unfollowUser,
    toggleFollow,
    resetError,
    setFollowStatus,
    initializeFollowState
  };
}
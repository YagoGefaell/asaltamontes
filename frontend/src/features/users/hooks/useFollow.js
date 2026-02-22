import { useState, useCallback } from 'react';
import { 
  followUserRequest, 
  unfollowUserRequest
} from '../services/users.service.js';

export function useFollow() {
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
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  const unfollowUser = useCallback(async (username) => {
    setLoading(true);
    setError(null);
    
    try {
      await unfollowUserRequest(username);
      setIsFollowing(false);
      setFollowersCount(prev => Math.max(0, prev - 1));
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

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

  // New method to initialize the follow state for a specific user
  const initializeFollowState = useCallback((userProfile) => {
    if (userProfile) {
      setFollowersCount(userProfile.followersCount || 0);
      setFollowingCount(userProfile.followingCount || 0);
      // If the API provides info about follow status, use it
      setIsFollowing(userProfile.isFollowedByCurrentUser || false);
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
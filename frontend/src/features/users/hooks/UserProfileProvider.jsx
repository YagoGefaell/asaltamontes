import { useState, useEffect } from "react";
import { useAuth } from "../../auth/hooks/useAuth.js";
import { getMeRequest, updateUserProfileRequest, getUsersByUsername, getUserByUsername } from "../services/users.service.js";
import { UserProfileContext } from "./user.context";

export function UserProfileProvider({ children }) {
    const { isAuthenticated } = useAuth();
    const [userProfile, setUserProfile] = useState(null);
    const [loading, setLoading] = useState(true);
  
    useEffect(() => {
        if (!isAuthenticated) {
          setUserProfile(null);
          setLoading(false);
          return;
        }
    
        const fetchProfile = async () => {
          setLoading(true);
          try {
            const data = await getMeRequest();
            setUserProfile(data || null);
          } catch (err) {
            setUserProfile(null);
          } finally {
            setLoading(false);
          }
        };
    
        fetchProfile();
    }, [isAuthenticated]);

    const updateUserProfile = async (profileData) => {
      setLoading(true);

      try {
        const updatedData = await updateUserProfileRequest(profileData);
        setUserProfile(updatedData);
        setLoading(false);
        return updatedData;
      } catch (err) {
        setLoading(false);
        throw err;
      }
    };

    const searchUsersByUsername = async (query) => {
      setLoading(true);

      try {
        const users = await getUsersByUsername(query);
        setLoading(false);
        return users;
      } catch (err) {
        setLoading(false);
        throw err;
      }
    };

    const getUserProfile = async (username) => {
      try {
        const user = await getUserByUsername(username);
        return user;
      } catch (err) {
        throw err;
      }
    };


  return (
    <UserProfileContext.Provider value={{ userProfile, loading, updateUserProfile, searchUsersByUsername, getUserProfile }}>
      {children}
    </UserProfileContext.Provider>
  );
}

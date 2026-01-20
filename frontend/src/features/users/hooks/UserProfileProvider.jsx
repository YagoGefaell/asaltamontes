import { useState, useEffect } from "react";
import { useAuth } from "../../auth/hooks/useAuth.js";
import { getMeRequest } from "../services/users.service.js";
import { UserProfileContext } from "./user.context";
import { updateUserProfileRequest } from "../services/users.service.js";

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
            setUserProfile(data);
          } catch (err) {
            setUserProfile(null);
            throw err;
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


  return (
    <UserProfileContext.Provider value={{ userProfile, loading, updateUserProfile }}>
      {children}
    </UserProfileContext.Provider>
  );
}

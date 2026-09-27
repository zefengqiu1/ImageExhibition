import { useNavigate } from 'react-router-dom';  // Use useNavigate instead of useHistory
import { useEffect } from 'react';

const useAuthRedirect = () => {
  const navigate = useNavigate();  // Hook to handle navigation

  useEffect(() => {
    const token = localStorage.getItem('token');

    if (!token) {
      navigate('/login');  // Use navigate() to redirect to the login page
    }
  }, [navigate]);
};

export default useAuthRedirect;


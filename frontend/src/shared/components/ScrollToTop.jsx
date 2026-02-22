import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Component that scrolls to top on route changes
 * Should be placed inside Router but outside of Routes
 */
function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    // Scroll to top when location changes
    window.scrollTo({
      top: 0,
      left: 0,
      behavior: 'instant' // Use 'smooth' for animated scroll
    });
  }, [pathname]);

  return null; // This component doesn't render anything
}

export default ScrollToTop;
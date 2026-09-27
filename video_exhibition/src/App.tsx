import List from './pages/List';
import MovieList from './pages/MovieList';
import DramaList from './pages/DramaList';
import VarietyList from './pages/VarietyList';
import SearchList from './pages/SearchList';
import AnalyticsPage from './pages/AnalyticsPage';
import VideoAdminPage from './pages/VideoAdminPage';
import './App.css';
import { Route, Routes } from 'react-router-dom';
import RankPage from './components/RankPage';
import ImageDetail from './components/ImageDetail';

function App() {
  return (
    <Routes>
      {/* <Route path="/" element={<Home />} /> */}
      <Route path="/" element={<List />} />
      <Route path="/list/movie" element={<MovieList />} />
      <Route path="/list/drama" element={<DramaList />} />
      <Route path="/list/variety" element={<VarietyList />} />
      <Route path="/search" element={<SearchList />} />
      <Route path="/rank/all" element={<RankPage />} />
      <Route path="/video/:id" element={<ImageDetail />} />
      <Route path="/analytics" element={<AnalyticsPage />} />
      <Route path="/admin/videos" element={<VideoAdminPage />} />
    </Routes>
  );
}

export default App;

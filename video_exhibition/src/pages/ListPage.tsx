import Footer from '../components/Footer';
import Header from '../components/Header';
import LabelSection from '../components/LabelSection';
import MainDisplay from '../components/MainDisplay';

interface ListPageProps {
  containerClassName?: string;
  theme: 'movie' | 'drama' | 'variety';
}

export default function ListPage({ containerClassName, theme }: ListPageProps) {
  return (
    <div className={containerClassName}>
      <Header />
      <LabelSection theme={theme} />
      <MainDisplay />
      <Footer />
    </div>
  );
}

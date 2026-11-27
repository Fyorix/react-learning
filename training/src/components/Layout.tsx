import { NavLink, Outlet } from "react-router";

export default function Layout() {
  return (
    <>
      <nav>
        <NavLink to="/" end>Accueil</NavLink> | <NavLink to="/about">À propos</NavLink>
      </nav>
      <main>
        <Outlet />
      </main>
    </>
  );
}

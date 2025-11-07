package dao;

import control.ConDB;
import modelo.Venta;
import modelo.Venta_has_producto;
import modelo.Producto;
import modelo.Usuarios;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VentaDAO {

    // CREAR VENTA
    public void agregar(Venta venta, List<Venta_has_producto> carrito) throws SQLException {
        Connection con = null;
        PreparedStatement psVenta = null;
        ResultSet rs = null;

        try {
            con = ConDB.conectar();
            con.setAutoCommit(false);

            String sqlVenta = "INSERT INTO venta (fecha_factura, descuento, abono, subtotal, valor_total, observaciones, usuarios_id_usuario) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            psVenta = con.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS);
            psVenta.setDate(1, new java.sql.Date(venta.getFecha_factura().getTime()));
            psVenta.setInt(2, venta.getDescuento());
            psVenta.setDouble(3, venta.getAbono());
            psVenta.setDouble(4, venta.getSubtotal());
            psVenta.setDouble(5, venta.getValor_total());
            psVenta.setString(6, venta.getObservaciones());
            psVenta.setInt(7, venta.getUsuarios_id_usuario());
            psVenta.executeUpdate();

            rs = psVenta.getGeneratedKeys();
            int idVenta = 0;
            if (rs.next()) idVenta = rs.getInt(1);

            for (Venta_has_producto item : carrito) {
                String sqlDet = "INSERT INTO venta_has_producto (venta_idfactura, productos_idproducto, cantidad, valor_unitario) VALUES (?, ?, ?, ?)";
                try (PreparedStatement psDet = con.prepareStatement(sqlDet)) {
                    psDet.setInt(1, idVenta);
                    psDet.setInt(2, item.getProducto().getIdproducto());
                    psDet.setInt(3, item.getCantidad());
                    psDet.setDouble(4, item.getValor_unitario());
                    psDet.executeUpdate();
                }

                String sqlStock = "UPDATE producto SET stock = stock - ? WHERE idproducto = ?";
                try (PreparedStatement psStock = con.prepareStatement(sqlStock)) {
                    psStock.setInt(1, item.getCantidad());
                    psStock.setInt(2, item.getProducto().getIdproducto());
                    psStock.executeUpdate();
                }
            }

            con.commit();
        } catch (SQLException e) {
            if (con != null) con.rollback();
            throw e;
        } finally {
            if (rs != null) rs.close();
            if (psVenta != null) psVenta.close();
            if (con != null) con.close();
        }
    }

    // LISTAR VENTAS
    public List<Venta> listar() {
        List<Venta> lista = new ArrayList<>();
        try (Connection con = ConDB.conectar()) {
            String sql = "SELECT v.*, u.p_nombre, u.p_apellido FROM venta v JOIN usuarios u ON v.usuarios_id_usuario = u.id_usuario";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Venta v = new Venta();
                v.setIdfactura(rs.getInt("idfactura"));
                v.setFecha_factura(rs.getDate("fecha_factura"));
                v.setDescuento(rs.getInt("descuento"));
                v.setAbono(rs.getDouble("abono"));
                v.setSubtotal(rs.getDouble("subtotal"));
                v.setValor_total(rs.getDouble("valor_total"));
                v.setObservaciones(rs.getString("observaciones"));
                v.setUsuarios_id_usuario(rs.getInt("usuarios_id_usuario"));

                Usuarios u = new Usuarios();
                u.setP_nombre(rs.getString("p_nombre"));
                u.setP_apellido(rs.getString("p_apellido"));
                v.setUsuario(u);

                lista.add(v);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }
// listar2
    public List<Venta> listarPorUsuario(int idUsuario) {
    List<Venta> lista = new ArrayList<>();
    try (Connection con = ConDB.conectar()) {
        String sql = "SELECT v.*, u.p_nombre, u.p_apellido FROM venta v " +
                     "JOIN usuarios u ON v.usuarios_id_usuario = u.id_usuario " +
                     "WHERE v.usuarios_id_usuario = ?";
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setInt(1, idUsuario);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            Venta v = new Venta();
            v.setIdfactura(rs.getInt("idfactura"));
            v.setFecha_factura(rs.getDate("fecha_factura"));
            v.setValor_total(rs.getDouble("valor_total"));

            Usuarios u = new Usuarios();
            u.setP_nombre(rs.getString("p_nombre"));
            u.setP_apellido(rs.getString("p_apellido"));
            v.setUsuario(u);

            lista.add(v);
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }
    return lista;
}

    // LISTAR DETALLE
    public List<Venta_has_producto> listarDetalle(int idVenta) {
    List<Venta_has_producto> lista = new ArrayList<>();
    try (Connection con = ConDB.conectar()) {
        String sql = "SELECT vhp.*, p.nombre_producto " +
                     "FROM venta_has_producto vhp " +
                     "JOIN producto p ON vhp.productos_idproducto = p.idproducto " +
                     "WHERE vhp.venta_idfactura = ?";
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setInt(1, idVenta);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            Venta_has_producto vhp = new Venta_has_producto();
            Venta v = new Venta();
            v.setIdfactura(rs.getInt("venta_idfactura"));
            vhp.setVenta(v);

            Producto p = new Producto();
            p.setIdproducto(rs.getInt("productos_idproducto"));
            p.setNombre_producto(rs.getString("nombre_producto"));
            vhp.setProducto(p);

            vhp.setCantidad(rs.getInt("cantidad"));
            vhp.setValor_unitario(rs.getDouble("valor_unitario"));

            lista.add(vhp);
        }
        System.out.println("DEBUG listarDetalle → Venta: " + idVenta + " Detalles encontrados: " + lista.size());
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return lista;
}
    //Actualizar venta
  public void actualizar(Venta venta, List<Venta_has_producto> carrito) throws SQLException {
    Connection con = null;
    PreparedStatement ps = null;
    ResultSet rs = null;

    try {
        con = ConDB.conectar();
        con.setAutoCommit(false);

        int idVenta = venta.getIdfactura();

        // 1) Obtener detalle antiguo (map: productoId -> cantidad)
        Map<Integer, Integer> viejo = new HashMap<>();
        String sqlOld = "SELECT productos_idproducto, cantidad FROM venta_has_producto WHERE venta_idfactura = ?";
        try (PreparedStatement psOld = con.prepareStatement(sqlOld)) {
            psOld.setInt(1, idVenta);
            try (ResultSet rsOld = psOld.executeQuery()) {
                while (rsOld.next()) {
                    viejo.put(rsOld.getInt("productos_idproducto"), rsOld.getInt("cantidad"));
                }
            }
        }

        // 2) Construir mapa nuevo (productoId -> cantidad)
        Map<Integer, Integer> nuevo = new HashMap<>();
        for (Venta_has_producto it : carrito) {
            nuevo.put(it.getProducto().getIdproducto(), it.getCantidad());
        }

        // 3) Calcular diferencias y validar stock (y aplicar actualizaciones de stock)
        // Para cada producto en la unión de keys:
        Set<Integer> allIds = new HashSet<>();
        allIds.addAll(viejo.keySet());
        allIds.addAll(nuevo.keySet());

        for (Integer prodId : allIds) {
            int qtyOld = viejo.getOrDefault(prodId, 0);
            int qtyNew = nuevo.getOrDefault(prodId, 0);
            int diff = qtyNew - qtyOld; // >0 -> restar stock; <0 -> sumar stock

            if (diff > 0) {
                // validar stock actual
                String sqlStockSel = "SELECT stock FROM producto WHERE idproducto = ?";
                int stockActual = 0;
                try (PreparedStatement psStockSel = con.prepareStatement(sqlStockSel)) {
                    psStockSel.setInt(1, prodId);
                    try (ResultSet rsStock = psStockSel.executeQuery()) {
                        if (rsStock.next()) {
                            stockActual = rsStock.getInt("stock");
                        } else {
                            throw new SQLException("Producto no encontrado: " + prodId);
                        }
                    }
                }
                if (stockActual < diff) {
                    throw new SQLException("Stock insuficiente para el producto id " + prodId + ". Disponible: " + stockActual + ", requerido: " + diff);
                }
                String sqlUpdStock = "UPDATE producto SET stock = stock - ? WHERE idproducto = ?";
                try (PreparedStatement psUpd = con.prepareStatement(sqlUpdStock)) {
                    psUpd.setInt(1, diff);
                    psUpd.setInt(2, prodId);
                    psUpd.executeUpdate();
                }
            } else if (diff < 0) {
                // aumentar stock (se devolvió cantidad)
                String sqlUpdStock = "UPDATE producto SET stock = stock + ? WHERE idproducto = ?";
                try (PreparedStatement psUpd = con.prepareStatement(sqlUpdStock)) {
                    psUpd.setInt(1, -diff);
                    psUpd.setInt(2, prodId);
                    psUpd.executeUpdate();
                }
            }
        }

        // 4) Actualizar cabecera de venta (fecha, subtotal, descuento, abono, valor_total, observaciones)
        String sqlUpdVenta = "UPDATE venta SET fecha_factura = ?, subtotal = ?, descuento = ?, abono = ?, valor_total = ?, observaciones = ? WHERE idfactura = ?";
        try (PreparedStatement psUpdVenta = con.prepareStatement(sqlUpdVenta)) {
            psUpdVenta.setDate(1, new java.sql.Date(venta.getFecha_factura().getTime()));
            psUpdVenta.setDouble(2, venta.getSubtotal());
            psUpdVenta.setInt(3, venta.getDescuento());
            psUpdVenta.setDouble(4, venta.getAbono());
            psUpdVenta.setDouble(5, venta.getValor_total());
            psUpdVenta.setString(6, venta.getObservaciones());
            psUpdVenta.setInt(7, idVenta);
            psUpdVenta.executeUpdate();
        }

        // 5) Reemplazar los detalles: eliminar y volver a insertar (ya ajustamos el stock)
        String sqlDelete = "DELETE FROM venta_has_producto WHERE venta_idfactura = ?";
        try (PreparedStatement psDel = con.prepareStatement(sqlDelete)) {
            psDel.setInt(1, idVenta);
            psDel.executeUpdate();
        }

        String sqlInsertDet = "INSERT INTO venta_has_producto (venta_idfactura, productos_idproducto, cantidad, valor_unitario) VALUES (?, ?, ?, ?)";
        try (PreparedStatement psIns = con.prepareStatement(sqlInsertDet)) {
            for (Venta_has_producto it : carrito) {
                psIns.setInt(1, idVenta);
                psIns.setInt(2, it.getProducto().getIdproducto());
                psIns.setInt(3, it.getCantidad());
                psIns.setDouble(4, it.getValor_unitario());
                psIns.addBatch();
            }
            psIns.executeBatch();
        }

        con.commit();
    } catch (SQLException e) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ex) {
                // log rollback failure
            }
        }
        throw e;
    } finally {
        if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
        if (ps != null) try { ps.close(); } catch (SQLException ignored) {}
        if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignored) {}
    }
}



    // ELIMINAR VENTA
    public void eliminar(int idVenta) throws SQLException {
        Connection con = null;
        try {
            con = ConDB.conectar();
            con.setAutoCommit(false);

            String sqlDet = "SELECT productos_idproducto, cantidad FROM venta_has_producto WHERE venta_idfactura = ?";
            List<Venta_has_producto> detalles = new ArrayList<>();
            try (PreparedStatement psDet = con.prepareStatement(sqlDet)) {
                psDet.setInt(1, idVenta);
                ResultSet rs = psDet.executeQuery();
                while (rs.next()) {
                    Venta_has_producto vhp = new Venta_has_producto();
                    Producto p = new Producto();
                    p.setIdproducto(rs.getInt("productos_idproducto"));
                    vhp.setProducto(p);
                    vhp.setCantidad(rs.getInt("cantidad"));
                    detalles.add(vhp);
                }
            }

            for (Venta_has_producto item : detalles) {
                String sqlStock = "UPDATE producto SET stock = stock + ? WHERE idproducto = ?";
                try (PreparedStatement psStock = con.prepareStatement(sqlStock)) {
                    psStock.setInt(1, item.getCantidad());
                    psStock.setInt(2, item.getProducto().getIdproducto());
                    psStock.executeUpdate();
                }
            }

            try (PreparedStatement ps = con.prepareStatement("DELETE FROM venta_has_producto WHERE venta_idfactura = ?")) {
                ps.setInt(1, idVenta);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM venta WHERE idfactura = ?")) {
                ps.setInt(1, idVenta);
                ps.executeUpdate();
            }

            con.commit();
        } catch (SQLException e) {
            if (con != null) con.rollback();
            throw e;
        } finally {
            if (con != null) con.close();
        }
    }
}

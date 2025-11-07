package control;

import dao.VentaDAO;
import dao.ProductoDAO;
import modelo.Producto;
import modelo.Venta;
import modelo.Venta_has_producto;
import modelo.Usuarios;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;

@ManagedBean
@SessionScoped
public class VentaBean implements Serializable {

    private Venta ventaActual;
    private Venta ventaSeleccionada;
    private List<Venta> listaVentas;
    private List<Venta_has_producto> carrito;            // contiene los productos actuales del formulario (nueva/editar)
    private List<Venta_has_producto> detallesVenta;      // para ver detalle

    private int usuarioSeleccionado; // ya no se usa para crear la venta (se mantiene por compatibilidad de bean)
    private Map<Integer, Number> cantidadesPorProducto;

    private VentaDAO ventaDAO;
    private ProductoDAO productoDAO;

    @ManagedProperty("#{productoBean}")
    private ProductoBean productoBean;

    @PostConstruct
    public void init() {
        ventaActual = new Venta();
        ventaActual.setFecha_factura(new Date());
        listaVentas = new ArrayList<>();
        carrito = new ArrayList<>();
        detallesVenta = new ArrayList<>();
        cantidadesPorProducto = new HashMap<>();
        ventaDAO = new VentaDAO();
        productoDAO = new ProductoDAO();
        listarVentas();
    }

    /**
     * Lista ventas: ADMIN -> todas; OPERARIO -> solo sus ventas --- CAMBIO:
     * ahora filtra por rol
     */
    public void listarVentas() {
        try {
            Usuarios usuarioLogueado = (Usuarios) FacesContext.getCurrentInstance()
                    .getExternalContext().getSessionMap().get("usuario");

            if (usuarioLogueado == null) {
                listaVentas = new ArrayList<>();
                return;
            }

            if ("administrador".equalsIgnoreCase(usuarioLogueado.getTipo_usu())) {
                listaVentas = ventaDAO.listar(); // método existente que devuelve todas las ventas (debe incluir usuario)
            } else {
                listaVentas = ventaDAO.listarPorUsuario(usuarioLogueado.getId_usuario()); // método que te mostré antes
            }
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error listando ventas: " + e.getMessage(), null));
        }
    }

    // ===========================
    // CREAR VENTA (sin cambios lógicos importantes)
    // ===========================
    public void guardarVenta() {
        try {
            Usuarios usuarioLogueado = (Usuarios) FacesContext.getCurrentInstance()
                    .getExternalContext().getSessionMap().get("usuario");

            if (usuarioLogueado == null) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "No hay usuario en sesión", null));
                return;
            }

            if (carrito == null || carrito.isEmpty()) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_WARN, "Debe agregar productos a la venta", ""));
                return;
            }

            // --- CAMBIO: asignar usuario logueado automáticamente ---
            ventaActual.setUsuarios_id_usuario(usuarioLogueado.getId_usuario());

            // Subtotal/total calculados desde el carrito
            ventaActual.setSubtotal(getSubtotal());
            ventaActual.setValor_total(getTotal());

            // fecha actual (no la pide user)
            ventaActual.setFecha_factura(new Date());

            // delega en el DAO — el DAO maneja stock y transacción
            ventaDAO.agregar(ventaActual, carrito);

            if (productoBean != null) {
                productoBean.cargarProductos();
            }

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Venta registrada exitosamente", null));

            limpiarFormulario();
            listarVentas();

        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error: " + e.getMessage(), null));
        }
    }

    private void limpiarFormulario() {
        ventaActual = new Venta();
        ventaActual.setFecha_factura(new Date());
        carrito = new ArrayList<>();
        cantidadesPorProducto = new HashMap<>();
        usuarioSeleccionado = 0;
    }

    // ===========================
    // AGREGAR / ELIMINAR PRODUCTOS (misma funcionalidad que antes, usable en edición)
    // ===========================
    public void agregarProducto(Producto p) {
        int cantidad = getCantidadProducto(p.getIdproducto());
        if (cantidad <= 0) {
            cantidad = 1;
        }
        if (p.getStock() < cantidad) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Stock insuficiente. Disponible: " + p.getStock(), ""));
            return;
        }

        Venta_has_producto existente = carrito.stream()
                .filter(item -> item.getProducto().getIdproducto() == p.getIdproducto())
                .findFirst().orElse(null);

        if (existente != null) {
            int nuevaCantidad = existente.getCantidad() + cantidad;
            if (nuevaCantidad > p.getStock()) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "No se puede superar el stock disponible: " + p.getStock(), ""));
                return;
            }
            existente.setCantidad(nuevaCantidad);
        } else {
            Venta_has_producto nuevo = new Venta_has_producto();
            nuevo.setProducto(p);
            nuevo.setCantidad(cantidad);
            nuevo.setValor_unitario(p.getPrecio_producto());
            carrito.add(nuevo);
        }
        cantidadesPorProducto.put(p.getIdproducto(), 1);
    }

    public void eliminarDelCarrito(Venta_has_producto item) {
        carrito.remove(item);
        // eliminar cantidad en el mapa para que no interfiera
        cantidadesPorProducto.remove(item.getProducto().getIdproducto());
    }

    /**
     * Actualiza la cantidad de un item del carrito tomando el valor del map
     * cantidadesPorProducto. --- CAMBIO: permite editar cantidades en la vista
     * de edición
     */
    public void actualizarCantidad(Venta_has_producto item) {
        try {
            int idProd = item.getProducto().getIdproducto();
            Number n = cantidadesPorProducto.getOrDefault(idProd, item.getCantidad());
            int nueva = Math.max(1, n.intValue());

            Producto prod = productoDAO.buscar(idProd);
            if (prod == null) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Producto no encontrado", null));
                return;
            }

            // diferencia real respecto a la cantidad anterior
            int diferencia = nueva - item.getCantidad();
            if (diferencia > 0 && diferencia > prod.getStock()) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR,
                                "Stock insuficiente. Disponible: " + prod.getStock(), null));
                return;
            }

            // actualiza la cantidad del carrito
            item.setCantidad(nueva);
            cantidadesPorProducto.put(idProd, nueva);

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Cantidad actualizada correctamente", null));

        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error actualizando cantidad: " + e.getMessage(), null));
        }
    }

    public double getSubtotal() {
        if (carrito == null || carrito.isEmpty()) {
            return 0;
        }
        return carrito.stream()
                .mapToDouble(i -> i.getCantidad() * i.getValor_unitario())
                .sum();
    }

    public double getTotal() {
        double total = getSubtotal();
        if (ventaActual != null) {
            if (ventaActual.getDescuento() > 0) {
                total -= getSubtotal() * ventaActual.getDescuento() / 100;
            }
            if (ventaActual.getAbono() > 0) {
                total -= ventaActual.getAbono();
            }
        }
        return Math.max(total, 0);
    }

    public int getCantidadProducto(int idProducto) {
        if (cantidadesPorProducto == null) {
            return 1;
        }
        Number n = cantidadesPorProducto.get(idProducto);
        return (n == null) ? 1 : Math.max(n.intValue(), 1);
    }

    // ===========================
    // VER DETALLE
    // ===========================
    public String verDetalle(Venta venta) {
        this.ventaSeleccionada = venta;
        this.detallesVenta = ventaDAO.listarDetalle(venta.getIdfactura());
        return "/ventas/detalleVenta.xhtml?faces-redirect=true";
    }

    // ===========================
    // ELIMINAR VENTA (ya existente)
    // ===========================
    public void eliminarVenta(int idVenta) {
        try {
            ventaDAO.eliminar(idVenta);
            listarVentas();
            if (productoBean != null) {
                productoBean.cargarProductos();
            }
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Venta eliminada y stock devuelto", null));
        } catch (SQLException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error eliminando: " + e.getMessage(), null));
        }
    }

    // ===========================
    // --- NUEVO: IR A EDITAR VENTA ---
    // Carga la venta y su detalle en el formulario (carrito) para editar
    // ===========================
    public String irEditarVenta(Venta v) {
        try {
            // cargar la venta completa (si tu DAO tiene un obtenerPorId sería ideal; aquí usamos la instancia)
            this.ventaActual = v;
            // cargar detalles en carrito (usamos listarDetalle existente)
            this.carrito = ventaDAO.listarDetalle(v.getIdfactura()); // lista de Venta_has_producto
            // poblar el mapa de cantidades para la UI
            cantidadesPorProducto = new HashMap<>();
            for (Venta_has_producto vh : carrito) {
                cantidadesPorProducto.put(vh.getProducto().getIdproducto(), vh.getCantidad());
            }
            return "/ventas/editarVenta.xhtml?faces-redirect=true";
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error preparando edición: " + e.getMessage(), null));
            return null;
        }
    }

    /**
     * --- CAMBIO IMPORTANTE: actualizarVenta --- Actualiza la venta completa
     * (cabecera + detalle). - Actualiza stocks según diferencias (sumar/restar)
     * - Reemplaza los detalles en venta_has_producto - Usa transacción dentro
     * de DAO
     */
    public void actualizarVenta() {
        try {
            if (carrito == null || carrito.isEmpty()) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_WARN, "El carrito no puede quedar vacío", null));
                return;
            }

            // sincronizar cantidades desde cantidadesPorProducto al carrito (por si el usuario editó inputs)
            for (Venta_has_producto item : carrito) {
                Number n = cantidadesPorProducto.get(item.getProducto().getIdproducto());
                if (n != null) {
                    int nueva = Math.max(1, n.intValue());
                    item.setCantidad(nueva);
                }
            }

            // recalcular montos
            ventaActual.setSubtotal(getSubtotal());
            ventaActual.setValor_total(getTotal());

            // --- fecha se actualiza a la fecha actual (según tu requerimiento) ---
            ventaActual.setFecha_factura(new Date());

            // delegar en DAO: el DAO hará la transacción y el ajuste de stock
            ventaDAO.actualizar(ventaActual, carrito);

            if (productoBean != null) {
                productoBean.cargarProductos();
            }

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Venta actualizada correctamente", null));

            // volver al listado
            listarVentas();
            FacesContext.getCurrentInstance().getExternalContext().redirect("listarVentas.xhtml");

        } catch (SQLException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error actualizando venta: " + e.getMessage(), null));
        } catch (IOException ioe) {
            // redirección fallida
        }
    }

    // ==== GETTERS / SETTERS ====
    public Venta getVentaActual() {
        return ventaActual;
    }

    public void setVentaActual(Venta ventaActual) {
        this.ventaActual = ventaActual;
    }

    public List<Venta> getListaVentas() {
        return listaVentas;
    }

    public void setListaVentas(List<Venta> listaVentas) {
        this.listaVentas = listaVentas;
    }

    public List<Venta_has_producto> getCarrito() {
        return carrito;
    }

    public void setCarrito(List<Venta_has_producto> carrito) {
        this.carrito = carrito;
    }

    public int getUsuarioSeleccionado() {
        return usuarioSeleccionado;
    }

    public void setUsuarioSeleccionado(int usuarioSeleccionado) {
        this.usuarioSeleccionado = usuarioSeleccionado;
    }

    public Map<Integer, Number> getCantidadesPorProducto() {
        return cantidadesPorProducto;
    }

    public void setCantidadesPorProducto(Map<Integer, Number> cantidadesPorProducto) {
        this.cantidadesPorProducto = cantidadesPorProducto;
    }

    public ProductoBean getProductoBean() {
        return productoBean;
    }

    public void setProductoBean(ProductoBean productoBean) {
        this.productoBean = productoBean;
    }

    public Venta getVentaSeleccionada() {
        return ventaSeleccionada;
    }

    public void setVentaSeleccionada(Venta ventaSeleccionada) {
        this.ventaSeleccionada = ventaSeleccionada;
    }

    public List<Venta_has_producto> getDetallesVenta() {
        return detallesVenta;
    }

    public void setDetallesVenta(List<Venta_has_producto> detallesVenta) {
        this.detallesVenta = detallesVenta;
    }
}
